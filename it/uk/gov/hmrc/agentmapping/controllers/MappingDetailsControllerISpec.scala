package uk.gov.hmrc.agentmapping.controllers

import java.time.LocalDateTime

import org.scalatest.Suite
import org.scalatestplus.play.ServerProvider
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.mvc.Http.Status
import uk.gov.hmrc.agentmapping.controller.MappingDetailsController
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmapping.repository.MappingDetailsRepository
import uk.gov.hmrc.agentmapping.stubs.{AuthStubs, SubscriptionStub}
import uk.gov.hmrc.agentmapping.support.ServerBaseISpec
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain

import scala.concurrent.ExecutionContext.Implicits.global

class MappingDetailsControllerISpec extends ServerBaseISpec with AuthStubs with SubscriptionStub {

  this: Suite with ServerProvider =>

  val arn: Arn = Arn("TARN0000001")

  val controller: MappingDetailsController = app.injector.instanceOf[MappingDetailsController]

  protected val repository: MappingDetailsRepository = app.injector.instanceOf[MappingDetailsRepository]

  override def beforeEach() {
    super.beforeEach()
    await(repository.drop)
    ()
  }

  "MappingDetailsController" should {

    val authProviderId = AuthProviderId("cred-123")
    val ggTag = GGTag("1234")
    val count = 10

    "POST /mappings/details/arn/:arn" should {

      "create a new record when one doesn't exist" in {

        val request: Request[JsValue] = FakeRequest("POST", "agent-mapping/mappings/details/arn/:arn").withBody(
          Json.parse(s"""{"authProviderId": "cred-123", "ggTag": "1234", "count": 10}"""))

        val result = controller.createOrUpdateRecord(arn)(request)

        status(result) shouldBe Status.CREATED

        await(repository.findByArn(arn)).get.arn shouldBe arn
        await(repository.findByArn(arn)).get should matchRecordIgnoringDateTime(
          MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, ggTag, count, LocalDateTime.now()))))
      }

      "update a records mappings when it does exist" in {

        val request: Request[JsValue] = FakeRequest("POST", "agent-mapping/mappings/details/arn/:arn").withBody(
          Json.parse(s"""{"authProviderId": "cred-456", "ggTag": "5678", "count": 20}"""))

        await(repository.create(
          MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, ggTag, count, LocalDateTime.now())))))

        val result = controller.createOrUpdateRecord(arn)(request)

        status(result) shouldBe Status.OK

        await(repository.findByArn(arn)).get.arn shouldBe arn
        await(repository.findByArn(arn)).get.mappingDetails.length shouldBe 2
        await(repository.findByArn(arn)).get should matchRecordIgnoringDateTime(
          MappingDetailsRepositoryRecord(
            arn,
            Seq(
              MappingDetails(authProviderId, ggTag, count, LocalDateTime.now()),
              MappingDetails(AuthProviderId("cred-456"), GGTag("5678"), 20, LocalDateTime.now()))
          ))
      }

      "return conflict if the mapping already exists" in {

        val request: Request[JsValue] = FakeRequest("POST", "agent-mapping/mappings/details/arn/:arn").withBody(
          Json.parse(s"""{"authProviderId": "cred-123", "ggTag": "1234", "count": 10}"""))

        await(repository.create(
          MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, ggTag, count, LocalDateTime.now())))))

        val result = controller.createOrUpdateRecord(arn)(request)

        status(result) shouldBe Status.CONFLICT
      }
    }
    "GET /mappings/details/arn/:arn" should {

      "find and return record if it exists for the arn" in {

        await(repository.create(
          MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, ggTag, count, LocalDateTime.now())))))

        val result =
          controller.findRecordByArn(arn)(FakeRequest("GET", "agent-mapping/mappings/details/arn/:arn"))

        status(result) shouldBe Status.OK
        jsonBodyOf(result).as[MappingDetailsRepositoryRecord] should matchRecordIgnoringDateTime(
          MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, ggTag, count, LocalDateTime.now()))))
      }

      "return not found if there is not record found for the arn" in {

        val result =
          controller.findRecordByArn(arn)(FakeRequest("GET", "agent-mapping/mappings/details/arn/:arn"))

        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "PUT /mappings/task-list/details/arn/:arn" should {

      val agentCode = "TZRXXV"

      "don't create mapping-details row for user no mappings (clean credentials)" in {

        givenUserIsAuthorisedForCreds(AgentCode.key, "AgentCode", agentCode, "cred-123", agentCodeOpt = None)
        givenNoMappingsExistForAuthProviderId(AuthProviderId("cred-123"))

        val result = controller.transferSubscriptionRecordToMappingDetails(arn)(
          FakeRequest("PUT", "agent-mapping/mappings/task-list/details/arn/:arn"))

        status(result) shouldBe Status.OK

        await(repository.findByArn(arn)) shouldBe None

      }

      "update the permanent mapping details store with the user mappings from the subscription journey record" in {

        givenUserIsAuthorisedForCreds(AgentCode.key, "AgentCode", agentCode, "cred-123", agentCodeOpt = None)
        givenUserMappingsExistsForAuthProviderId(
          AuthProviderId("cred-123"),
          Seq(
            UserMapping(
              AuthProviderId("cred-456"),
              Some(domain.AgentCode("code-1")),
              Seq(
                AgentEnrolment(HmrcGtsAgentRef, IdentifierValue("id-gts")),
                AgentEnrolment(IRAgentReferenceCt, IdentifierValue("id-ct"))),
              5,
              "ggTag-1"
            ),
            UserMapping(
              AuthProviderId("cred-789"),
              Some(domain.AgentCode("code-2")),
              Seq(
                AgentEnrolment(SdltStorn, IdentifierValue("id-sdl")),
                AgentEnrolment(AgentCharId, IdentifierValue("id-char"))),
              10,
              "ggTag-2"
            )
          )
        )

        val result = controller.transferSubscriptionRecordToMappingDetails(arn)(
          FakeRequest("PUT", "agent-mapping/mappings/task-list/details/arn/:arn"))

        status(result) shouldBe Status.CREATED
        await(repository.findByArn(arn)).get should matchRecordIgnoringDateTime(
          MappingDetailsRepositoryRecord(
            arn,
            Seq(
              MappingDetails(AuthProviderId("cred-456"), GGTag("ggTag-1"), 5, LocalDateTime.now()),
              MappingDetails(AuthProviderId("cred-789"), GGTag("ggTag-2"), 10, LocalDateTime.now())
            )
          ))
      }

      "return not found when no user mappings are found in the subscription journey record" in {

        givenUserIsAuthorisedForCreds(AgentCode.key, "AgentCode", agentCode, "cred-123", agentCodeOpt = None)
        givenUserMappingsNotFoundForAuthProviderId(AuthProviderId("cred-123"))

        val result = controller.transferSubscriptionRecordToMappingDetails(arn)(
          FakeRequest("PUT", "agent-mapping/mappings/task-list/details/arn/:arn"))

        status(result) shouldBe Status.NOT_FOUND
      }
    }
  }
}
