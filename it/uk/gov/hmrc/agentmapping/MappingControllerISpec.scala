package uk.gov.hmrc.agentmapping

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.AbstractModule
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSClient
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmapping.audit.AgentMappingEvent
import uk.gov.hmrc.agentmapping.repository.MappingRepository
import uk.gov.hmrc.agentmapping.stubs.{AuthStubs, DataStreamStub, DesStubs}
import uk.gov.hmrc.agentmapping.support.{MongoApp, Resource, WireMockSupport}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class MappingControllerISpec extends UnitSpec with MongoApp with WireMockSupport with DesStubs with AuthStubs with DataStreamStub {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val client: WSClient = AhcWSClient()

  private val utr = Utr("2000000000")
  private val saAgentReference = "A1111A"
  val createMappingRequest: Resource = createMappingRequest()

  def createMappingRequest(requestUtr: Utr = utr, requestArn: Arn = registeredArn): Resource = {
    new Resource(s"/agent-mapping/mappings/${requestUtr.value}/${requestArn.value}/$saAgentReference", port)
  }

  def findMappingsRequest(requestArn: Arn = registeredArn): Resource = {
    new Resource(s"/agent-mapping/mappings/${requestArn.value}", port)
  }

  implicit val hc = HeaderCarrier()
  implicit val fakeRequest = FakeRequest("GET", "/agent-mapping/add-code")

  private val findMappingsRequest: Resource = findMappingsRequest()

  private val repo: MappingRepository = app.injector.instanceOf[MappingRepository]

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder = {
    new GuiceApplicationBuilder().configure(
      mongoConfiguration ++
        Map(
          "microservice.services.auth.port" -> wireMockPort,
          "microservice.services.des.port" -> wireMockPort,
          "auditing.consumer.baseUri.host" -> wireMockHost,
          "auditing.consumer.baseUri.port" -> wireMockPort
        )
    ).overrides(new TestGuiceModule)
  }

  private class TestGuiceModule extends AbstractModule {
    override def configure(): Unit = {}
  }

  override def beforeEach() {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  "mapping creation requests" should {
    "return created upon success" in {
      individualRegistrationExists(utr)
      createMappingRequest.putEmpty().status shouldBe 201
    }

    "return a successful audit event with known facts set to true" in {
      individualRegistrationExists(utr)
      givenAuthority("testCredId")
      givenAuditConnector()
      createMappingRequest.putEmpty().status shouldBe 201

      verifyAuditRequestSent(1,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "true",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName"->"known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/A1111A"
        )
      )
    }

    "return conflict when the mapping already exists" in {
      individualRegistrationExists(utr)
      givenAuthority("testCredId")
      givenAuditConnector()
      createMappingRequest.putEmpty().status shouldBe 201
      createMappingRequest.putEmpty().status shouldBe 409

      verifyAuditRequestSent(2,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "true",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName"->"known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/A1111A"
        )
      )

      verifyAuditRequestSent(1,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "true",
          "duplicate" -> "true")
      )

    }

    "return forbidden when the supplied arn does not match the DES business partner record arn" in {
      individualRegistrationExists(utr)
      givenAuthority("testCredId")
      givenAuditConnector()
      new Resource(s"/agent-mapping/mappings/${utr.value}/TARN0000001/${saAgentReference}", port).putEmpty().status shouldBe 403

      verifyAuditRequestSent(1,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "false",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "TARN0000001",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName"->"known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/TARN0000001/A1111A"
        )
      )
    }

    "return forbidden when there is no arn on the DES business partner record" in {
      individualRegistrationExistsWithoutArn(utr)
      givenAuthority("testCredId")
      givenAuditConnector()
      createMappingRequest.putEmpty().status shouldBe 403

      verifyAuditRequestSent(1,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "false",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName"->"known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/A1111A"
        )
      )
    }

    "return forbidden when the DES business partner record does not exist" in {
      registrationDoesNotExist(utr)
      givenAuthority("testCredId")
      givenAuditConnector()
      createMappingRequest.putEmpty().status shouldBe 403

      verifyAuditRequestSent(1,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "false",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName"->"known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/A1111A"
        )
      )
    }

    "return bad request when the UTR is invalid" in {
      val response = createMappingRequest(requestUtr = Utr("A_BAD_UTR")).putEmpty()
      response.status shouldBe 400
      (response.json \ "message").as[String] shouldBe """"A_BAD_UTR" is not a valid UTR"""
    }

    "return bad request when the ARN is invalid" in {
      val response = createMappingRequest(requestArn = Arn("A_BAD_ARN")).putEmpty()
      response.status shouldBe 400
      (response.json \ "message").as[String] shouldBe """"A_BAD_ARN" is not a valid ARN"""
    }
  }


  "find mapping requests" should {
    "return 200 status with a json body representing the mappings that match the supplied arn" in {
      await(repo.createMapping(registeredArn, SaAgentReference(saAgentReference)))
      await(repo.createMapping(registeredArn, SaAgentReference("A1111B")))

      val response = findMappingsRequest.get()

      response.status shouldBe 200
      val body = response.body
      body shouldBe """{"mappings":[{"arn":"AARN0000002","saAgentReference":"A1111A"},{"arn":"AARN0000002","saAgentReference":"A1111B"}]}"""
    }

    "return 404 when there are no mappings that match the supplied arn" in {
      findMappingsRequest.get().status shouldBe 404
    }
  }
}
