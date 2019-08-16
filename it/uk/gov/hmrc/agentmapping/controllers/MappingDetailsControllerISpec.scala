package uk.gov.hmrc.agentmapping.controllers

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.AbstractModule
import org.scalatest.matchers.{MatchResult, Matcher}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSClient
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.mvc.Http.Status
import uk.gov.hmrc.agentmapping.controller.MappingDetailsController
import uk.gov.hmrc.agentmapping.model.{AuthProviderId, GGTag, MappingDetails, MappingDetailsRepositoryRecord}
import uk.gov.hmrc.agentmapping.repository.MappingDetailsRepository
import uk.gov.hmrc.agentmapping.stubs.{AuthStubs, DataStreamStub, SubscriptionStub}
import uk.gov.hmrc.agentmapping.support.{MongoApp, WireMockSupport}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class MappingDetailsControllerISpec extends MappingDetailsControllerISpecSetup {

  val arn: Arn = Arn("TARN0000001")

  val controller: MappingDetailsController = app.injector.instanceOf[MappingDetailsController]

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
  }
}

sealed trait MappingDetailsControllerISpecSetup
    extends UnitSpec
    with MongoApp
    with WireMockSupport
    with AuthStubs
    with DataStreamStub
    with SubscriptionStub {

  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val client: WSClient = AhcWSClient()

  override implicit lazy val app: Application = appBuilder.build()

  protected val repository: MappingDetailsRepository = app.injector.instanceOf[MappingDetailsRepository]

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        mongoConfiguration ++
          Map(
            "microservice.services.auth.port"               -> wireMockPort,
            "microservice.services.agent-subscription.port" -> wireMockPort,
            "microservice.services.agent-subscription.host" -> wireMockHost,
            "auditing.consumer.baseUri.host"                -> wireMockHost,
            "auditing.consumer.baseUri.port"                -> wireMockPort,
            "migrate-repositories"                          -> "false"
          ))
      .overrides(new TestGuiceModule)

  private class TestGuiceModule extends AbstractModule {
    override def configure(): Unit = {}
  }

  override def beforeEach() {
    super.beforeEach()
    await(repository.ensureIndexes)
    givenAuditConnector()
    ()
  }

  def matchRecordIgnoringDateTime(
    mappingDisplayRecord: MappingDetailsRepositoryRecord): Matcher[MappingDetailsRepositoryRecord] =
    new Matcher[MappingDetailsRepositoryRecord] {
      override def apply(left: MappingDetailsRepositoryRecord): MatchResult = left match {
        case record
            if mappingDisplayRecord.arn == record.arn &&
              mappingDisplayRecord.mappingDetails.map(m => (m.ggTag, m.count)) == record.mappingDetails
                .map(m => (m.ggTag, m.count)) =>
          MatchResult(matches = true, "", "")
      }
    }
}
