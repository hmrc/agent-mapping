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
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http.HeaderCarrier
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

  def deleteMappingsRequest(requestArn: Arn = registeredArn): Resource = {
    new Resource(s"/agent-mapping/test-only/mappings/${requestArn.value}", port)
  }

  implicit val hc = HeaderCarrier()
  implicit val fakeRequest = FakeRequest("GET", "/agent-mapping/add-code")

  private val findMappingsRequest: Resource = findMappingsRequest()
  private val deleteMappingsRequest: Resource = deleteMappingsRequest()

  private val repo: MappingRepository = app.injector.instanceOf[MappingRepository]

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder = {
    new GuiceApplicationBuilder().configure(
      mongoConfiguration ++
        Map(
          "microservice.services.auth.port" -> wireMockPort,
          "microservice.services.des.port" -> wireMockPort,
          "auditing.consumer.baseUri.host" -> wireMockHost,
          "auditing.consumer.baseUri.port" -> wireMockPort,
          "application.router" -> "testOnlyDoNotUseInAppConf.Routes"
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
      givenUserAuthorisedFor("IR-SA-AGENT","IRAgentReference","A1111A","testCredId")
      givenIndividualRegistrationExists(utr)
      createMappingRequest.putEmpty().status shouldBe 201
    }

    "return a successful audit event with known facts set to true" in {
      givenUserAuthorisedFor("IR-SA-AGENT","IRAgentReference","A1111A","testCredId")
      givenIndividualRegistrationExists(utr)
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

      verifyAuditRequestSent(1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "saAgentRef" -> "A1111A",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "false"),
        tags = Map(
          "transactionName"->"create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/A1111A"
        )
      )

    }

    "return conflict when the mapping already exists" in {
      givenUserAuthorisedFor("IR-SA-AGENT","IRAgentReference","A1111A","testCredId")
      givenIndividualRegistrationExists(utr)
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
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "saAgentRef" -> "A1111A",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "true"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/A1111A"
        )
      )

    }

    "return forbidden when the supplied arn does not match the DES business partner record arn" in {
      givenUserAuthorisedFor("IR-SA-AGENT","IRAgentReference","A1111A","testCredId")
      givenIndividualRegistrationExists(utr)
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
      givenUserAuthorisedFor("IR-SA-AGENT","IRAgentReference","A1111A","testCredId")
      givenIndividualRegistrationExistsWithoutArn(utr)
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
      givenUserAuthorisedFor("IR-SA-AGENT","IRAgentReference","A1111A","testCredId")
      givenRegistrationDoesNotExist(utr)
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

    "return unauthenticated" when {
      "unauthenticated user attempts to create mapping" in {
        givenUserNotAuthorisedWithError("MissingBearerToken")
        givenIndividualRegistrationExists(utr)
        createMappingRequest.putEmpty().status shouldBe 401
      }
    }

    "return forbidden" when {
      "user with Agent affinity group and HMRC-AS-AGENT enrolment attempts to create mapping" in {
        givenUserAuthorisedFor("HMRC-AS-AGENT","AgentReferenceNumber","TARN000003","testCredId")
        givenIndividualRegistrationExists(utr)
        createMappingRequest.putEmpty().status shouldBe 403
      }
      "authenticated user with IR-SA-AGENT enrolment but without Agent Affinity group attempts to create mapping" in {
        givenUserAuthorisedFor("IR-SA-AGENT","IRAgentReference","2000000000","testCredId", AffinityGroup.Individual)
        givenIndividualRegistrationExists(utr)
        createMappingRequest.putEmpty().status shouldBe 403
      }
      "user with Agent affinity group and IR-SA-AGENT enrolment attempts to create mapping for invalid saAgentReference" in {
        givenUserAuthorisedFor("IR-SA-AGENT","IRAgentReference","3000000000","testCredId")
        givenIndividualRegistrationExists(utr)
        createMappingRequest.putEmpty().status shouldBe 403
      }
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

  "delete" should {
    "return no content when a record is deleted" in {
      await(repo.createMapping(registeredArn, SaAgentReference(saAgentReference)))

      val foundResponse = findMappingsRequest.get()
      foundResponse.status shouldBe 200

      val deleteResponse = deleteMappingsRequest.delete()
      deleteResponse.status shouldBe 204

      val notFoundResponse = findMappingsRequest.get()
      notFoundResponse.status shouldBe 404
    }

    "return no content when no record is deleted" in {
      val deleteResponse = deleteMappingsRequest.delete()
      deleteResponse.status shouldBe 204
    }
  }
}
