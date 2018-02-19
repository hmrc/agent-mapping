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
import uk.gov.hmrc.agentmapping.model.{ Identifier, Identifiers }
import uk.gov.hmrc.agentmapping.repository.{ AgentCodeMappingRepository, SaAgentReferenceMappingRepository, VatAgentReferenceMappingRepository }
import uk.gov.hmrc.agentmapping.stubs.{ AuthStubs, DataStreamStub, DesStubs }
import uk.gov.hmrc.agentmapping.support.{ MongoApp, Resource, WireMockSupport }
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.auth.core.{ AffinityGroup, Enrolment, EnrolmentIdentifier }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.agentmapping.model.Names._

class MappingControllerISpec extends UnitSpec with MongoApp with WireMockSupport with DesStubs with AuthStubs with DataStreamStub {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val client: WSClient = AhcWSClient()

  private val utr = Utr("2000000000")
  private val saOnlyIdentifier = Identifiers(Seq(Identifier(IRAgentReference, "A1111A")))
  private val vatOnlyIdentifier = Identifiers(Seq(Identifier(AgentRefNo, "101747696")))
  private val saAndVatIdentifiers = Identifiers(Seq(Identifier(IRAgentReference, "A1111A"), Identifier(AgentRefNo, "101747696")))

  def createMappingRequest(identifiers: Identifiers, requestUtr: Utr = utr, requestArn: Arn = registeredArn): Resource = {
    new Resource(s"/agent-mapping/mappings/${requestUtr.value}/${requestArn.value}/$identifiers", port)
  }

  def createFreeMappingRequest(identifiers: String, requestUtr: Utr = utr, requestArn: Arn = registeredArn): Resource = {
    new Resource(s"/agent-mapping/mappings/${requestUtr.value}/${requestArn.value}/$identifiers", port)
  }

  def findMappingsRequest(requestArn: Arn = registeredArn): Resource = {
    new Resource(s"/agent-mapping/mappings/${requestArn.value}", port)
  }

  def findSAMappingsRequest(requestArn: Arn = registeredArn): Resource = {
    new Resource(s"/agent-mapping/mappings/sa/${requestArn.value}", port)
  }

  def findVATMappingsRequest(requestArn: Arn = registeredArn): Resource = {
    new Resource(s"/agent-mapping/mappings/vat/${requestArn.value}", port)
  }

  def findAgentCodeMappingsRequest(requestArn: Arn = registeredArn): Resource = {
    new Resource(s"/agent-mapping/mappings/agentcode/${requestArn.value}", port)
  }

  def deleteMappingsRequest(requestArn: Arn = registeredArn): Resource = {
    new Resource(s"/agent-mapping/test-only/mappings/${requestArn.value}", port)
  }

  implicit val hc = HeaderCarrier()
  implicit val fakeRequest = FakeRequest("GET", "/agent-mapping/add-code")

  private val saRepo: SaAgentReferenceMappingRepository = app.injector.instanceOf[SaAgentReferenceMappingRepository]
  private val vatRepo: VatAgentReferenceMappingRepository = app.injector.instanceOf[VatAgentReferenceMappingRepository]
  private val agentCodeRepo: AgentCodeMappingRepository = app.injector.instanceOf[AgentCodeMappingRepository]

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder = {
    new GuiceApplicationBuilder().configure(
      mongoConfiguration ++
        Map(
          "microservice.services.auth.port" -> wireMockPort,
          "microservice.services.des.port" -> wireMockPort,
          "auditing.consumer.baseUri.host" -> wireMockHost,
          "auditing.consumer.baseUri.port" -> wireMockPort,
          "application.router" -> "testOnlyDoNotUseInAppConf.Routes")).overrides(new TestGuiceModule)
  }

  private class TestGuiceModule extends AbstractModule {
    override def configure(): Unit = {}
  }

  override def beforeEach() {
    super.beforeEach()
    await(saRepo.ensureIndexes)
    await(vatRepo.ensureIndexes)
    await(agentCodeRepo.ensureIndexes)
    givenAuditConnector()
  }

  "mapping creation requests" should {
    "return created upon success for SA" in {
      givenUserAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "A1111A", "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenIndividualRegistrationExists(utr)
      createMappingRequest(saOnlyIdentifier).putEmpty().status shouldBe 201
    }

    "return created upon success for SA w/o agent code" in {
      givenUserAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "A1111A", "testCredId", agentCodeOpt = None)
      givenIndividualRegistrationExists(utr)
      createMappingRequest(saOnlyIdentifier).putEmpty().status shouldBe 201
    }

    "return created upon success for VAT" in {
      givenUserAuthorisedFor(`HMCE-VAT-AGNT`, AgentRefNo, "101747696", "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenIndividualRegistrationExists(utr)
      createMappingRequest(vatOnlyIdentifier).putEmpty().status shouldBe 201
    }

    "return created upon success for VAT w/o agent code" in {
      givenUserAuthorisedFor(`HMCE-VAT-AGNT`, AgentRefNo, "101747696", "testCredId", agentCodeOpt = None)
      givenIndividualRegistrationExists(utr)
      createMappingRequest(vatOnlyIdentifier).putEmpty().status shouldBe 201
    }

    "return bad request upon failure for VAT validation" in {
      givenUserAuthorisedFor(`HMCE-VAT-AGNT`, AgentRefNo, "B1111B", "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenIndividualRegistrationExists(utr)
      createFreeMappingRequest("AgentRefNo~B1111B").putEmpty().status shouldBe 400
    }

    "return created upon success for SA and VAT" in {
      givenUserAuthorisedForMultiple(Set(
        Enrolment(`IR-SA-AGENT`, Seq(EnrolmentIdentifier(IRAgentReference, "A1111A")), "Activated"),
        Enrolment(`HMCE-VAT-AGNT`, Seq(EnrolmentIdentifier(AgentRefNo, "101747696")), "Activated")), "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenIndividualRegistrationExists(utr)
      createMappingRequest(saAndVatIdentifiers).putEmpty().status shouldBe 201
    }

    "return forbidden when agent got single enrolment but tries to map more identifiers" in {
      givenUserAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "A1111A", "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenIndividualRegistrationExists(utr)
      createMappingRequest(saAndVatIdentifiers).putEmpty().status shouldBe 403
    }

    "return a successful audit event with known facts set to true for SA" in {
      givenUserAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "A1111A", "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenIndividualRegistrationExists(utr)
      createMappingRequest(saOnlyIdentifier).putEmpty().status shouldBe 201

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "true",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName" -> "known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "saAgentRef" -> "A1111A",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "false"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "agentCode" -> "TZRXXV",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "false"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A"))

    }

    "return a successful audit event with known facts set to true for VAT" in {
      givenUserAuthorisedFor(`HMCE-VAT-AGNT`, AgentRefNo, "101747696", "testCredId", agentCodeOpt = Some("TZRXXA"))
      givenIndividualRegistrationExists(utr)
      createMappingRequest(vatOnlyIdentifier).putEmpty().status shouldBe 201

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "true",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName" -> "known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/AgentRefNo~101747696"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "vatAgentRef" -> "101747696",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "false"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/AgentRefNo~101747696"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "agentCode" -> "TZRXXA",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "false"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/AgentRefNo~101747696"))

    }

    "return conflict when the mapping already exists for sa" in {
      givenUserAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "A1111A", "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenIndividualRegistrationExists(utr)
      createMappingRequest(saOnlyIdentifier).putEmpty().status shouldBe 201
      createMappingRequest(saOnlyIdentifier).putEmpty().status shouldBe 409

      verifyAuditRequestSent(
        2,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "true",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName" -> "known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "saAgentRef" -> "A1111A",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "true"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A"))

    }

    "return conflict when the mapping already exists for vat" in {
      givenUserAuthorisedFor(`HMCE-VAT-AGNT`, AgentRefNo, "101747696", "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenIndividualRegistrationExists(utr)
      createMappingRequest(vatOnlyIdentifier).putEmpty().status shouldBe 201
      createMappingRequest(vatOnlyIdentifier).putEmpty().status shouldBe 409

      verifyAuditRequestSent(
        2,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "true",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName" -> "known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/AgentRefNo~101747696"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "vatAgentRef" -> "101747696",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "true"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/AgentRefNo~101747696"))

    }

    "return conflict when the mapping already exists for SA but not for VAT" in {
      givenUserAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "A1111A", "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenUserAuthorisedForMultiple(Set(
        Enrolment(`IR-SA-AGENT`, Seq(EnrolmentIdentifier(IRAgentReference, "A1111A")), "Activated"),
        Enrolment(`HMCE-VAT-AGNT`, Seq(EnrolmentIdentifier(AgentRefNo, "101747696")), "Activated")), "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenIndividualRegistrationExists(utr)
      createMappingRequest(saOnlyIdentifier).putEmpty().status shouldBe 201
      createMappingRequest(saAndVatIdentifiers).putEmpty().status shouldBe 201

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "true",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName" -> "known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "true",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName" -> "known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A~AgentRefNo~101747696"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "saAgentRef" -> "A1111A",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "false"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "saAgentRef" -> "A1111A",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "true"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A~AgentRefNo~101747696"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "vatAgentRef" -> "101747696",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "false"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A~AgentRefNo~101747696"))

    }

    "return conflict when the mapping already exists for both SA and VAT" in {
      givenUserAuthorisedForMultiple(Set(
        Enrolment(`IR-SA-AGENT`, Seq(EnrolmentIdentifier(IRAgentReference, "A1111A")), "Activated"),
        Enrolment(`HMCE-VAT-AGNT`, Seq(EnrolmentIdentifier(AgentRefNo, "101747696")), "Activated")), "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenIndividualRegistrationExists(utr)
      createMappingRequest(saAndVatIdentifiers).putEmpty().status shouldBe 201
      createMappingRequest(saAndVatIdentifiers).putEmpty().status shouldBe 409

      verifyAuditRequestSent(
        2,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "true",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName" -> "known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A~AgentRefNo~101747696"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "saAgentRef" -> "A1111A",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "false"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A~AgentRefNo~101747696"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "saAgentRef" -> "A1111A",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "true"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A~AgentRefNo~101747696"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "vatAgentRef" -> "101747696",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "false"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A~AgentRefNo~101747696"))

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.CreateMapping,
        detail = Map(
          "vatAgentRef" -> "101747696",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId",
          "duplicate" -> "true"),
        tags = Map(
          "transactionName" -> "create-mapping",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A~AgentRefNo~101747696"))

    }

    "return forbidden when the supplied arn does not match the DES business partner record arn" in {
      givenUserAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "A1111A", "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenIndividualRegistrationExists(utr)

      new Resource(s"/agent-mapping/mappings/${utr.value}/TARN0000001/${saOnlyIdentifier}", port).putEmpty().status shouldBe 403

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "false",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "TARN0000001",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName" -> "known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/TARN0000001/IRAgentReference~A1111A"))
    }

    "return forbidden when there is no arn on the DES business partner record" in {
      givenUserAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "A1111A", "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenIndividualRegistrationExistsWithoutArn(utr)
      createMappingRequest(saOnlyIdentifier).putEmpty().status shouldBe 403

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "false",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName" -> "known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A"))
    }

    "return forbidden when the DES business partner record does not exist" in {
      givenUserAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "A1111A", "testCredId", agentCodeOpt = Some("TZRXXV"))
      givenRegistrationDoesNotExist(utr)

      createMappingRequest(saOnlyIdentifier).putEmpty().status shouldBe 403

      verifyAuditRequestSent(
        1,
        event = AgentMappingEvent.KnownFactsCheck,
        detail = Map(
          "knownFactsMatched" -> "false",
          "utr" -> "2000000000",
          "agentReferenceNumber" -> "AARN0000002",
          "authProviderId" -> "testCredId"),
        tags = Map(
          "transactionName" -> "known-facts-check",
          "path" -> "/agent-mapping/mappings/2000000000/AARN0000002/IRAgentReference~A1111A"))
    }

    "return bad request when the UTR is invalid" in {

      val response = createMappingRequest(saOnlyIdentifier, requestUtr = Utr("A_BAD_UTR")).putEmpty()
      response.status shouldBe 400
      (response.json \ "message").as[String] shouldBe "bad request"
    }

    "return bad request when the ARN is invalid" in {

      val response = createMappingRequest(saOnlyIdentifier, requestArn = Arn("A_BAD_ARN")).putEmpty()
      response.status shouldBe 400
      (response.json \ "message").as[String] shouldBe "bad request"
    }

    "return unauthenticated" when {
      "unauthenticated user attempts to create mapping" in {

        givenUserNotAuthorisedWithError("MissingBearerToken")
        givenIndividualRegistrationExists(utr)
        createMappingRequest(saOnlyIdentifier).putEmpty().status shouldBe 401
      }
    }

    "return forbidden" when {
      "user with Agent affinity group and HMRC-AS-AGENT enrolment attempts to create mapping" in {

        givenUserAuthorisedFor("HMRC-AS-AGENT", "AgentReferenceNumber", "TARN000003", "testCredId", agentCodeOpt = Some("TZRXXV"))
        givenIndividualRegistrationExists(utr)
        createMappingRequest(saOnlyIdentifier).putEmpty().status shouldBe 403
      }
      "authenticated user with IR-SA-AGENT enrolment but without Agent Affinity group attempts to create mapping" in {

        givenUserAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "2000000000", "testCredId", AffinityGroup.Individual, Some("TZRXXV"))
        givenIndividualRegistrationExists(utr)
        createMappingRequest(saOnlyIdentifier).putEmpty().status shouldBe 403
      }
      "user with Agent affinity group and IR-SA-AGENT enrolment attempts to create mapping for invalid saAgentReference" in {

        givenUserAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "3000000000", "testCredId", agentCodeOpt = Some("TZRXXV"))
        givenIndividualRegistrationExists(utr)
        createMappingRequest(saOnlyIdentifier).putEmpty().status shouldBe 403
      }
    }
  }

  "find mapping requests" should {
    "return 200 status with a json body representing the mappings that match the supplied arn" in {
      await(saRepo.createMapping(registeredArn, "A1111A"))
      await(saRepo.createMapping(registeredArn, "A1111B"))

      val response = findMappingsRequest().get()

      response.status shouldBe 200
      val body = response.body
      body shouldBe """{"mappings":[{"arn":"AARN0000002","saAgentReference":"A1111A"},{"arn":"AARN0000002","saAgentReference":"A1111B"}]}"""
    }

    "return 200 status with a json body representing the mappings that match the supplied arn for sa" in {
      await(saRepo.createMapping(registeredArn, "A1111A"))
      await(saRepo.createMapping(registeredArn, "A1111B"))

      val response = findSAMappingsRequest().get()

      response.status shouldBe 200
      val body = response.body
      body shouldBe """{"mappings":[{"arn":"AARN0000002","saAgentReference":"A1111A"},{"arn":"AARN0000002","saAgentReference":"A1111B"}]}"""
    }

    "return 200 status with a json body representing the mappings that match the supplied arn for vat" in {
      await(vatRepo.createMapping(registeredArn, "101747696"))
      await(vatRepo.createMapping(registeredArn, "101747641"))

      val response = findVATMappingsRequest().get()

      response.status shouldBe 200
      val body = response.body

      body should include("""{"arn":"AARN0000002","vrn":"101747696"}""")
      body should include("""{"arn":"AARN0000002","vrn":"101747641"}""")
    }

    "return 200 status with a json body representing the mappings that match the supplied arn for agent code" in {
      await(agentCodeRepo.createMapping(registeredArn, "ABCDE1"))
      await(agentCodeRepo.createMapping(registeredArn, "ABCDE2"))

      val response = findAgentCodeMappingsRequest().get()

      response.status shouldBe 200
      val body = response.body

      body should include("""{"arn":"AARN0000002","agentCode":"ABCDE1"}""")
      body should include("""{"arn":"AARN0000002","agentCode":"ABCDE2"}""")
    }

    "return 404 when there are no mappings that match the supplied arn" in {
      findMappingsRequest().get().status shouldBe 404
    }

    "return 404 when there are no mappings that match the supplied arn for sa" in {
      findSAMappingsRequest().get().status shouldBe 404
    }

    "return 404 when there are no mappings that match the supplied arn for vat" in {
      findVATMappingsRequest().get().status shouldBe 404
    }
  }

  "delete" should {
    "return no content when a record is deleted" in {
      await(saRepo.createMapping(registeredArn, "foo"))
      await(vatRepo.createMapping(registeredArn, "foo"))
      await(agentCodeRepo.createMapping(registeredArn, "foo"))

      val foundResponse = findMappingsRequest().get()
      foundResponse.status shouldBe 200

      val deleteResponse = deleteMappingsRequest().delete()
      deleteResponse.status shouldBe 204

      val notFoundResponse = findMappingsRequest().get()
      notFoundResponse.status shouldBe 404
    }

    "return no content when no record is deleted" in {
      val deleteResponse = deleteMappingsRequest().delete()
      deleteResponse.status shouldBe 204
    }
  }
}
