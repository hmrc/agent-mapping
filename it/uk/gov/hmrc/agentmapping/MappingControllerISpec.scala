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
import uk.gov.hmrc.agentmapping.model.Service
import uk.gov.hmrc.agentmapping.model.Service._
import uk.gov.hmrc.agentmapping.repository.MappingRepositories
import uk.gov.hmrc.agentmapping.stubs.{AuthStubs, DataStreamStub, DesStubs}
import uk.gov.hmrc.agentmapping.support.{MongoApp, Resource, WireMockSupport}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MappingControllerISpec extends MappingControllerISpecSetup {

  private val utr = Utr("2000000000")
  private val agentCode = "TZRXXV"

  val IRAgentReference = "IRAgentReference"
  val AgentRefNo = "AgentRefNo"

  def createMappingRequest(requestUtr: Utr = utr, requestArn: Arn = registeredArn): Resource =
    new Resource(s"/agent-mapping/mappings/${requestUtr.value}/${requestArn.value}", port)

  def createMappingRequestDeprecatedRoute(requestUtr: Utr = utr, requestArn: Arn = registeredArn): Resource =
    new Resource(
      s"/agent-mapping/mappings/${requestUtr.value}/${requestArn.value}/IRAgentReference~A1111A~AgentRefNo~101747696",
      port)

  def findMappingsRequest(requestArn: Arn = registeredArn): Resource =
    new Resource(s"/agent-mapping/mappings/${requestArn.value}", port)

  def findSAMappingsRequest(requestArn: Arn = registeredArn): Resource =
    new Resource(s"/agent-mapping/mappings/sa/${requestArn.value}", port)

  def findVATMappingsRequest(requestArn: Arn = registeredArn): Resource =
    new Resource(s"/agent-mapping/mappings/vat/${requestArn.value}", port)

  def findAgentCodeMappingsRequest(requestArn: Arn = registeredArn): Resource =
    new Resource(s"/agent-mapping/mappings/agentcode/${requestArn.value}", port)

  def findMappingsRequestByKey(key: String)(requestArn: Arn = registeredArn): Resource =
    new Resource(s"/agent-mapping/mappings/key/$key/arn/${requestArn.value}", port)

  def deleteMappingsRequest(requestArn: Arn = registeredArn): Resource =
    new Resource(s"/agent-mapping/test-only/mappings/${requestArn.value}", port)

  case class TestFixture(service: Service.Name, identifierKey: String, identifierValue: String) {
    val key = Service.keyFor(service)
  }

  val AgentCodeTestFixture = TestFixture(AgentCode, "AgentCode", agentCode)

  val IRSAAGENTTestFixture = TestFixture(`IR-SA-AGENT`, IRAgentReference, "A1111A")
  val HMCEVATAGNTTestFixture = TestFixture(`HMCE-VAT-AGNT`, AgentRefNo, "101747696")
  val IRCTAGENTTestFixture = TestFixture(`IR-CT-AGENT`, IRAgentReference, "B2121C")
  val HMRCGTSAGNTTestFixture = TestFixture(`HMRC-GTS-AGNT`, "HMRCGTSAGENTREF", "AB8964622K")
  val HMRCNOVRNAGNTTestFixture = TestFixture(`HMRC-NOVRN-AGNT`, "VATAgentRefNo", "FGH79/96KUJ")
  val HMRCCHARAGENTTestFixture = TestFixture(`HMRC-CHAR-AGENT`, "AGENTCHARID", "FGH79/96KUJ")
  val HMRCMGDAGNTTestFixture = TestFixture(`HMRC-MGD-AGNT`, "HMRCMGDAGENTREF", "737B.89")
  val IRPAYEAGENTTestFixture = TestFixture(`IR-PAYE-AGENT`, IRAgentReference, "F9876J")
  val IRSDLTAGENTTestFixture = TestFixture(`IR-SDLT-AGENT`, "STORN", "AAA0008")

  val fixtures = Seq(
    IRSAAGENTTestFixture,
    HMCEVATAGNTTestFixture,
    IRCTAGENTTestFixture,
    HMRCGTSAGNTTestFixture,
    HMRCNOVRNAGNTTestFixture,
    HMRCCHARAGENTTestFixture,
    HMRCMGDAGNTTestFixture,
    IRPAYEAGENTTestFixture,
    IRSDLTAGENTTestFixture
  )

  "MappingController" should {

    // Test each fixture in isolation first
    fixtures.foreach { f =>
      s"capture ${Service.asString(f.service)} enrolment" when {
        "return created upon success" in {
          givenUserIsAuthorisedFor(f)
          givenIndividualRegistrationExists(utr)
          createMappingRequest().putEmpty().status shouldBe 201
        }

        "return created upon success using deprecated route" in {
          givenUserIsAuthorisedFor(f)
          givenIndividualRegistrationExists(utr)
          createMappingRequestDeprecatedRoute().putEmpty().status shouldBe 201
        }

        "return created upon success w/o agent code" in {
          givenUserIsAuthorisedFor(f.service, f.identifierKey, f.identifierValue, "testCredId", agentCodeOpt = None)
          givenIndividualRegistrationExists(utr)
          createMappingRequest().putEmpty().status shouldBe 201
        }

        "return a successful audit event with known facts set to true" in {
          givenUserIsAuthorisedFor(f)
          givenIndividualRegistrationExists(utr)
          createMappingRequest().putEmpty().status shouldBe 201

          verifyKnownFactsCheckAuditEventSent(1)
          verifyCreateMappingAuditEventSent(f)
          verifyCreateMappingAuditEventSent(AgentCodeTestFixture)
        }

        "return conflict when the mapping already exists" in {
          givenUserIsAuthorisedFor(f)
          givenIndividualRegistrationExists(utr)
          createMappingRequest().putEmpty().status shouldBe 201
          createMappingRequest().putEmpty().status shouldBe 409

          verifyKnownFactsCheckAuditEventSent(2)
          verifyCreateMappingAuditEventSent(f)
        }

      }
    }

    // Then test all fixtures at once
    s"capture all ${fixtures.size} enrolments" when {
      s"return created upon success" in {
        givenUserIsAuthorisedForMultiple(fixtures)
        givenIndividualRegistrationExists(utr)
        createMappingRequest().putEmpty().status shouldBe 201
      }
    }

    if (fixtures.size > 1) {
      // Then test different split sets of fixtures
      val splitFixtures: Seq[(Seq[TestFixture], Seq[TestFixture])] =
        (1 until Math.max(5, fixtures.size)).map(fixtures.splitAt) ++ (1 until Math.max(4, fixtures.size))
          .map(fixtures.reverse.splitAt)

      splitFixtures.foreach {
        case (left, right) =>
          s"return created when we add all, but the mappings already exists for some [${left.map(f => Service.asString(f.service)).mkString(",")}]" in {
            givenIndividualRegistrationExists(utr)
            givenUserIsAuthorisedForMultiple(left)
            createMappingRequest().putEmpty().status shouldBe 201

            givenUserIsAuthorisedForMultiple(fixtures)
            createMappingRequest().putEmpty().status shouldBe 201

            verifyKnownFactsCheckAuditEventSent(2)
            fixtures.foreach(f => verifyCreateMappingAuditEventSent(f))
            verifyCreateMappingAuditEventSent(AgentCodeTestFixture)
          }

          s"return conflict when all mappings already exists, but we try to add [${left.map(f => Service.asString(f.service)).mkString(",")}]" in {
            givenIndividualRegistrationExists(utr)
            givenUserIsAuthorisedForMultiple(fixtures)
            createMappingRequest().putEmpty().status shouldBe 201

            givenUserIsAuthorisedForMultiple(left)
            createMappingRequest().putEmpty().status shouldBe 409

            givenUserIsAuthorisedForMultiple(right)
            createMappingRequest().putEmpty().status shouldBe 409

            verifyKnownFactsCheckAuditEventSent(3)
            fixtures.foreach(f => verifyCreateMappingAuditEventSent(f))
            verifyCreateMappingAuditEventSent(AgentCodeTestFixture)
          }
      }
    }

    "return forbidden when the supplied arn does not match the DES business partner record arn" in {
      givenUserIsAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "A1111A", "testCredId", agentCodeOpt = Some(agentCode))
      givenIndividualRegistrationExists(utr)

      new Resource(s"/agent-mapping/mappings/${utr.value}/TARN0000001", port).putEmpty().status shouldBe 403

      verifyKnownFactsCheckAuditEventSent(1, matched = false, arn = "TARN0000001")
    }

    "return forbidden when there is no arn on the DES business partner record" in {
      givenUserIsAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "A1111A", "testCredId", agentCodeOpt = Some(agentCode))
      givenIndividualRegistrationExistsWithoutArn(utr)
      createMappingRequest().putEmpty().status shouldBe 403

      verifyKnownFactsCheckAuditEventSent(1, matched = false)
    }

    "return forbidden when the DES business partner record does not exist" in {
      givenUserIsAuthorisedFor(`IR-SA-AGENT`, IRAgentReference, "A1111A", "testCredId", agentCodeOpt = Some(agentCode))
      givenRegistrationDoesNotExist(utr)

      createMappingRequest().putEmpty().status shouldBe 403

      verifyKnownFactsCheckAuditEventSent(1, matched = false)
    }

    "return bad request when the UTR is invalid" in {

      val response = createMappingRequest(requestUtr = Utr("A_BAD_UTR")).putEmpty()
      response.status shouldBe 400
      (response.json \ "message").as[String] shouldBe "bad request"
    }

    "return bad request when the ARN is invalid" in {

      val response = createMappingRequest(requestArn = Arn("A_BAD_ARN")).putEmpty()
      response.status shouldBe 400
      (response.json \ "message").as[String] shouldBe "bad request"
    }

    "return unauthenticated" when {
      "unauthenticated user attempts to create mapping" in {

        givenUserNotAuthorisedWithError("MissingBearerToken")
        givenIndividualRegistrationExists(utr)
        createMappingRequest().putEmpty().status shouldBe 401
      }
    }

    "return forbidden" when {
      "user with Agent affinity group and HMRC-AS-AGENT enrolment attempts to create mapping" in {

        givenUserIsAuthorisedFor(
          "HMRC-AS-AGENT",
          "AgentReferenceNumber",
          "TARN000003",
          "testCredId",
          agentCodeOpt = Some(agentCode))
        givenIndividualRegistrationExists(utr)
        createMappingRequest().putEmpty().status shouldBe 403
      }
      "authenticated user with IR-SA-AGENT enrolment but without Agent Affinity group attempts to create mapping" in {

        givenUserIsAuthorisedFor(
          `IR-SA-AGENT`,
          IRAgentReference,
          "2000000000",
          "testCredId",
          AffinityGroup.Individual,
          Some(agentCode))
        givenIndividualRegistrationExists(utr)
        createMappingRequest().putEmpty().status shouldBe 403
      }
    }
  }

  "find mapping requests" should {
    "return 200 status with a json body representing the mappings that match the supplied arn" in {
      await(saRepo.store(registeredArn, "A1111A"))
      await(saRepo.store(registeredArn, "A1111B"))

      val response = findMappingsRequest().get()

      response.status shouldBe 200
      val body = response.body
      body shouldBe """{"mappings":[{"arn":"AARN0000002","saAgentReference":"A1111A"},{"arn":"AARN0000002","saAgentReference":"A1111B"}]}"""
    }

    "return 200 status with a json body representing the mappings that match the supplied arn for sa" in {
      await(saRepo.store(registeredArn, "A1111A"))
      await(saRepo.store(registeredArn, "A1111B"))

      val response = findSAMappingsRequest().get()

      response.status shouldBe 200
      val body = response.body
      body shouldBe """{"mappings":[{"arn":"AARN0000002","saAgentReference":"A1111A"},{"arn":"AARN0000002","saAgentReference":"A1111B"}]}"""
    }

    "return 200 status with a json body representing the mappings that match the supplied arn for vat" in {
      await(vatRepo.store(registeredArn, "101747696"))
      await(vatRepo.store(registeredArn, "101747641"))

      val response = findVATMappingsRequest().get()

      response.status shouldBe 200
      val body = response.body

      body should include("""{"arn":"AARN0000002","vrn":"101747696"}""")
      body should include("""{"arn":"AARN0000002","vrn":"101747641"}""")
    }

    "return 200 status with a json body representing the mappings that match the supplied arn for agent code" in {
      await(agentCodeRepo.store(registeredArn, "ABCDE1"))
      await(agentCodeRepo.store(registeredArn, "ABCDE2"))

      val response = findAgentCodeMappingsRequest().get()

      response.status shouldBe 200
      val body = response.body

      body should include("""{"arn":"AARN0000002","agentCode":"ABCDE1"}""")
      body should include("""{"arn":"AARN0000002","agentCode":"ABCDE2"}""")
    }

    Seq(
      IRCTAGENTTestFixture,
      HMRCGTSAGNTTestFixture,
      HMRCNOVRNAGNTTestFixture,
      HMRCCHARAGENTTestFixture,
      HMRCMGDAGNTTestFixture,
      IRPAYEAGENTTestFixture,
      IRSDLTAGENTTestFixture
    ).foreach { f =>
      s"return 200 status with a json body representing the mappings that match the supplied arn for ${f.key}" in {
        val repo = repositories.get(f.service)
        await(repo.store(registeredArn, "ABCDE123456"))
        await(repo.store(registeredArn, "ABCDE298980"))

        val response = findMappingsRequestByKey(f.key)().get()

        response.status shouldBe 200
        val body = response.body

        body should include("""{"arn":"AARN0000002","identifier":"ABCDE298980"}""")
        body should include("""{"arn":"AARN0000002","identifier":"ABCDE123456"}""")
      }

      s"return 404 when there are no ${f.key} mappings that match the supplied arn" in {
        findMappingsRequestByKey(f.key)().get.status shouldBe 404
      }
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
      await(saRepo.store(registeredArn, "foo"))
      await(vatRepo.store(registeredArn, "foo"))
      await(agentCodeRepo.store(registeredArn, "foo"))

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

  private def givenUserIsAuthorisedFor(f: TestFixture): Unit =
    givenUserIsAuthorisedFor(
      f.service,
      f.identifierKey,
      f.identifierValue,
      "testCredId",
      agentCodeOpt = Some(agentCode))

  private def givenUserIsAuthorisedForMultiple(fixtures: Seq[TestFixture]): Unit =
    givenUserIsAuthorisedForMultiple(asEnrolments(fixtures), "testCredId", agentCodeOpt = Some(agentCode))

  private def asEnrolments(fixtures: Seq[TestFixture]): Set[Enrolment] =
    fixtures
      .map(
        f =>
          Enrolment(
            Service.asString(f.service),
            Seq(EnrolmentIdentifier(f.identifierKey, f.identifierValue)),
            "Activated"))
      .toSet

  private def verifyCreateMappingAuditEventSent(f: TestFixture, duplicate: Boolean = false): Unit =
    verifyAuditRequestSent(
      1,
      event = AgentMappingEvent.CreateMapping,
      detail = Map(
        "identifier"           -> f.identifierValue,
        "identifierType"       -> Service.asString(f.service),
        "agentReferenceNumber" -> "AARN0000002",
        "authProviderId"       -> "testCredId",
        "duplicate"            -> s"$duplicate"
      ),
      tags = Map("transactionName" -> "create-mapping", "path" -> "/agent-mapping/mappings/2000000000/AARN0000002")
    )

  private def verifyKnownFactsCheckAuditEventSent(
    times: Int,
    matched: Boolean = true,
    arn: String = "AARN0000002",
    utr: String = "2000000000") =
    verifyAuditRequestSent(
      times,
      event = AgentMappingEvent.KnownFactsCheck,
      detail = Map(
        "knownFactsMatched"        -> s"$matched",
        "utr"                      -> utr,
        "agentReferenceNumber"     -> arn,
        "authProviderId"           -> "testCredId"),
      tags = Map("transactionName" -> "known-facts-check", "path" -> s"/agent-mapping/mappings/$utr/$arn")
    )

}

sealed trait MappingControllerISpecSetup
    extends UnitSpec with MongoApp with WireMockSupport with DesStubs with AuthStubs with DataStreamStub {

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val client: WSClient = AhcWSClient()
  implicit val hc = HeaderCarrier()
  implicit val fakeRequest = FakeRequest("GET", "/agent-mapping/add-code")

  protected val repositories = app.injector.instanceOf[MappingRepositories]

  val saRepo = repositories.get(`IR-SA-AGENT`)
  val vatRepo = repositories.get(`HMCE-VAT-AGNT`)
  val agentCodeRepo = repositories.get(AgentCode)

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        mongoConfiguration ++
          Map(
            "microservice.services.auth.port" -> wireMockPort,
            "microservice.services.des.port"  -> wireMockPort,
            "auditing.consumer.baseUri.host"  -> wireMockHost,
            "auditing.consumer.baseUri.port"  -> wireMockPort,
            "application.router"              -> "testOnlyDoNotUseInAppConf.Routes",
            "migrate-repositories"            -> "false"
          ))
      .overrides(new TestGuiceModule)

  private class TestGuiceModule extends AbstractModule {
    override def configure(): Unit = {}
  }

  override def beforeEach() {
    super.beforeEach()
    await(Future.sequence(repositories.map(_.ensureIndexes)))
    givenAuditConnector()
  }
}
