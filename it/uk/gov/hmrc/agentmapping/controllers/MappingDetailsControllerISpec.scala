package uk.gov.hmrc.agentmapping.controllers

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{ControllerComponents, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import play.mvc.Http.Status
import uk.gov.hmrc.agentmapping.auth.AuthActions
import uk.gov.hmrc.agentmapping.connector.SubscriptionConnector
import uk.gov.hmrc.agentmapping.controller.MappingDetailsController
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmapping.repository.MappingDetailsRepository
import uk.gov.hmrc.agentmapping.stubs.{AuthStubs, SubscriptionStub}
import uk.gov.hmrc.agentmapping.support.ServerBaseISpec
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class MappingDetailsControllerISpec
    extends ServerBaseISpec
    with AuthStubs
    with SubscriptionStub
    with DefaultPlayMongoRepositorySupport[MappingDetailsRepositoryRecord] {

  override def beforeEach(): Unit = {
    super.beforeEach()
    commonStubs()
    ()
  }

  override val patience: PatienceConfig = patienceConfig

  val arn: Arn = Arn("TARN0000001")

  override protected val repository: MappingDetailsRepository =
    new MappingDetailsRepository(mongoComponent)

  val authActions = app.injector.instanceOf[AuthActions]
  val cc = app.injector.instanceOf[ControllerComponents]
  val subscriptionConnector = app.injector.instanceOf[SubscriptionConnector]

  lazy val controller: MappingDetailsController =
    new MappingDetailsController(repository, authActions, cc, subscriptionConnector)

  val authProviderId = AuthProviderId("cred-123")
  val ggTag = GGTag("1234")
  val c = 10

  "POST /mappings/details/arn/:arn" should {

    "create a new record when one doesn't exist" in {

      val request: Request[JsValue] = FakeRequest("POST", "agent-mapping/mappings/details/arn/:arn")
        .withBody(Json.parse(s"""{"authProviderId": "cred-123", "ggTag": "1234", "count": 10}"""))
        .withHeaders("Authorization" -> "Bearer XYZ")

      val result = controller.createOrUpdateRecord(arn)(request)

      status(result) shouldBe Status.CREATED

      repository.findByArn(arn).futureValue.get.arn shouldBe arn
      repository.findByArn(arn).futureValue.get should matchRecordIgnoringDateTime(
        MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, ggTag, c, LocalDateTime.now())))
      )
    }

    "update a records mappings when it does exist" in {

      val request: Request[JsValue] = FakeRequest("POST", "agent-mapping/mappings/details/arn/:arn").withBody(
        Json.parse(s"""{"authProviderId": "cred-456", "ggTag": "5678", "count": 20}""")
      )

      repository
        .create(MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, ggTag, c, LocalDateTime.now()))))
        .futureValue

      val result = controller.createOrUpdateRecord(arn)(request)

      status(result) shouldBe Status.OK

      repository.findByArn(arn).futureValue.get.arn shouldBe arn
      repository.findByArn(arn).futureValue.get.mappingDetails.length shouldBe 2
      repository.findByArn(arn).futureValue.get should matchRecordIgnoringDateTime(
        MappingDetailsRepositoryRecord(
          arn,
          Seq(
            MappingDetails(authProviderId, ggTag, c, LocalDateTime.now()),
            MappingDetails(AuthProviderId("cred-456"), GGTag("5678"), 20, LocalDateTime.now())
          )
        )
      )
    }

    "return conflict if the mapping already exists" in {

      val request: Request[JsValue] = FakeRequest("POST", "agent-mapping/mappings/details/arn/:arn")
        .withBody(Json.parse(s"""{"authProviderId": "cred-123", "ggTag": "1234", "count": 10}"""))
        .withHeaders("Authorization" -> "Bearer XYZ")

      repository
        .create(MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, ggTag, c, LocalDateTime.now()))))
        .futureValue

      val result = controller.createOrUpdateRecord(arn)(request)

      status(result) shouldBe Status.CONFLICT
    }
  }
  "GET /mappings/details/arn/:arn" should {

    "find and return record if it exists for the arn" in {

      repository
        .create(MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, ggTag, c, LocalDateTime.now()))))
        .futureValue

      val result =
        controller.findRecordByArn(arn)(
          FakeRequest("GET", "agent-mapping/mappings/details/arn/:arn")
            .withHeaders("Authorization" -> "Bearer XYZ")
        )

      status(result) shouldBe Status.OK
      contentAsJson(result).as[MappingDetailsRepositoryRecord] should matchRecordIgnoringDateTime(
        MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, ggTag, c, LocalDateTime.now())))
      )
    }

    "return not found if there is not record found for the arn" in {

      val result =
        controller.findRecordByArn(arn)(
          FakeRequest("GET", "agent-mapping/mappings/details/arn/:arn")
            .withHeaders("Authorization" -> "Bearer XYZ")
        )

      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "PUT /mappings/task-list/details/arn/:arn" should {

    val agentCode = "TZRXXV"

    "don't create mapping-details row for user no mappings (clean credentials)" in {

      givenUserIsAuthorisedForCreds(AgentCode.key, "AgentCode", agentCode, "cred-123", agentCodeOpt = None)
      givenNoMappingsExistForAuthProviderId(AuthProviderId("cred-123"))

      val result = controller.transferSubscriptionRecordToMappingDetails(arn)(
        FakeRequest("PUT", "agent-mapping/mappings/task-list/details/arn/:arn")
          .withHeaders("Authorization" -> "Bearer XYZ")
      )

      status(result) shouldBe Status.OK

      repository.findByArn(arn).futureValue shouldBe None

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
              AgentEnrolment(IRAgentReferenceCt, IdentifierValue("id-ct"))
            ),
            5,
            "ggTag-1"
          ),
          UserMapping(
            AuthProviderId("cred-789"),
            Some(domain.AgentCode("code-2")),
            Seq(
              AgentEnrolment(SdltStorn, IdentifierValue("id-sdl")),
              AgentEnrolment(AgentCharId, IdentifierValue("id-char"))
            ),
            10,
            "ggTag-2"
          )
        )
      )

      val result = controller.transferSubscriptionRecordToMappingDetails(arn)(
        FakeRequest("PUT", "agent-mapping/mappings/task-list/details/arn/:arn")
          .withHeaders("Authorization" -> "Bearer XYZ")
      )

      status(result) shouldBe Status.CREATED
      repository.findByArn(arn).futureValue.get should matchRecordIgnoringDateTime(
        MappingDetailsRepositoryRecord(
          arn,
          Seq(
            MappingDetails(AuthProviderId("cred-456"), GGTag("ggTag-1"), 5, LocalDateTime.now()),
            MappingDetails(AuthProviderId("cred-789"), GGTag("ggTag-2"), 10, LocalDateTime.now())
          )
        )
      )
    }

    "return not found when no user mappings are found in the subscription journey record" in {

      givenUserIsAuthorisedForCreds(AgentCode.key, "AgentCode", agentCode, "cred-123", agentCodeOpt = None)
      givenUserMappingsNotFoundForAuthProviderId(AuthProviderId("cred-123"))

      val result = controller.transferSubscriptionRecordToMappingDetails(arn)(
        FakeRequest("PUT", "agent-mapping/mappings/task-list/details/arn/:arn")
          .withHeaders("Authorization" -> "Bearer XYZ")
      )

      status(result) shouldBe Status.NOT_FOUND
    }
  }
}
