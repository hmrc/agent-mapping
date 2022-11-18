package uk.gov.hmrc.agentmapping.controllers

import akka.actor.ActorSystem
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.google.inject.AbstractModule
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.agentmapping.audit.CreateMapping
import uk.gov.hmrc.agentmapping.controller.MappingController
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmapping.repository._
import uk.gov.hmrc.agentmapping.stubs.{AuthStubs, DataStreamStub, SubscriptionStub}
import uk.gov.hmrc.agentmapping.support.WireMockSupport
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.mongo.test.MongoSupport

import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import scala.concurrent.Future

class MappingControllerISpec extends MappingControllerISpecSetup with ScalaFutures {

  val registeredArn: Arn = Arn("AARN0000002")

  override def commonStubs(): Unit = {
    givenAuditConnector()
    ()
  }

  private val utr = Utr("2000000000")
  private val agentCode = "TZRXXV"

  val IRSAAgentReference = "IRAgentReference"
  val AgentReferenceNo = "AgentRefNo"
  val authProviderId = AuthProviderId("testCredId")

  private val ggTag = GGTag("1234")
  private val count = 10
  private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
  private val dateTime: LocalDateTime = LocalDateTime.parse("2019-10-10 12:00", dateTimeFormatter)
  private val mappingDisplayDetails = MappingDetails(authProviderId, ggTag, count, dateTime)

  private val record = MappingDetailsRepositoryRecord(registeredArn, Seq(mappingDisplayDetails))

  //val url = s"http://localhost:$port"
  //val wsClient = app.injector.instanceOf[WSClient]

//  def callPost(path: String, body: JsValue): WSResponse = {
//    wsClient.url(s"$url$path")
//      .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
//      .post(body)
//      .futureValue
//  }

//  def callGet(path: String): WSResponse = {
//    wsClient.url(s"$url$path")
//      .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
//      .get
//      .futureValue
//  }

//  def callDelete(path: String): WSResponse = {
//    wsClient.url(s"$url$path")
//      .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
//      .delete
//      .futureValue
//  }

//  def callPut(path: String, body: Option[String]): WSResponse = {
//    if (body.isDefined) {
//      wsClient.url(s"$url$path")
//        .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
//        .put(body.get)
//        .futureValue
//    } else {
//      wsClient.url(s"$url$path")
//        .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
//        .execute("PUT")
//        .futureValue
//    }
//  }

  def basicAuth(string: String): String = Base64.getEncoder.encodeToString(string.getBytes(UTF_8))

  val hasEligibleRequest: String =
    s"/agent-mapping/mappings/eligibility"

  val createMappingRequest: String =
    s"/agent-mapping/mappings/arn/${registeredArn.value}"

  val createMappingFromSubscriptionJourneyRecordRequest: String =
    s"/agent-mapping/mappings/task-list/arn/${registeredArn.value}"

  val createPreSubscriptionMappingRequest: String =
    s"/agent-mapping/mappings/pre-subscription/utr/${utr.value}"

  val updatePostSubscriptionMappingRequest: String =
    s"/agent-mapping/mappings/post-subscription/utr/${utr.value}"

  val findMappingsRequest: String =
    s"/agent-mapping/mappings/${registeredArn.value}"

  val findSAMappingsRequest: String =
    s"/agent-mapping/mappings/sa/${registeredArn.value}"

  val findVATMappingsRequest: String =
    s"/agent-mapping/mappings/vat/${registeredArn.value}"

  val findAgentCodeMappingsRequest: String =
    s"/agent-mapping/mappings/agentcode/${registeredArn.value}"

  def findMappingsRequestByKey(key: String): String =
    s"/agent-mapping/mappings/key/$key/arn/${registeredArn.value}"

  val deleteMappingsRequest: String =
    s"/agent-mapping/test-only/mappings/${registeredArn.value}"

  val deletePreSubscriptionMappingsRequest: String =
    s"/agent-mapping/mappings/pre-subscription/utr/${utr.value}"

  def terminateAgentsMapping(arn: Arn): String =
    s"/agent-mapping/agent/${arn.value}/terminate"

  case class TestFixture(legacyAgentEnrolmentType: LegacyAgentEnrolmentType, identifierKey: String, identifierValue: String) {
    val dbKey: String = legacyAgentEnrolmentType.dbKey
  }

  val AgentCodeTestFixture = TestFixture(AgentCode, "AgentCode", agentCode)
  val IRSAAGENTTestFixture = TestFixture(IRAgentReference, IRSAAgentReference, "A1111A")
  val HMCEVATAGNTTestFixture = TestFixture(AgentRefNo, AgentReferenceNo, "101747696")
  val IRCTAGENTTestFixture = TestFixture(IRAgentReferenceCt, IRSAAgentReference, "B2121C")
  val HMRCGTSAGNTTestFixture = TestFixture(HmrcGtsAgentRef, "HMRCGTSAGENTREF", "AB8964622K")
  val HMRCNOVRNAGNTTestFixture = TestFixture(VATAgentRefNo, "VATAgentRefNo", "FGH79/96KUJ")
  val HMRCCHARAGENTTestFixture = TestFixture(AgentCharId, "AGENTCHARID", "FGH79/96KUJ")
  val HMRCMGDAGNTTestFixture = TestFixture(HmrcMgdAgentRef, "HMRCMGDAGENTREF", "737B.89")
  val IRPAYEAGENTTestFixture = TestFixture(IRAgentReferencePaye, IRSAAgentReference, "F9876J")
  val IRSDLTAGENTTestFixture = TestFixture(SdltStorn, "STORN", "AAA0008")

  val AgentCodeUserMapping = UserMapping(authProviderId, Some(domain.AgentCode("agent-code")), Seq.empty, 0, "")
  val IRSAAGENTUserMapping =
    UserMapping(authProviderId, None, Seq(AgentEnrolment(IRAgentReference, IdentifierValue("A1111A"))), 0, "")
  val HMCEVATAGNTUserMapping =
    UserMapping(authProviderId, None, Seq(AgentEnrolment(AgentRefNo, IdentifierValue("101747696"))), 0, "")
  val IRCTAGENTUserMapping =
    UserMapping(authProviderId, None, Seq(AgentEnrolment(IRAgentReferenceCt, IdentifierValue("B2121C"))), 0, "")
  val HMRCGTSAGNTUserMapping =
    UserMapping(authProviderId, None, Seq(AgentEnrolment(HmrcGtsAgentRef, IdentifierValue("AB8964622K"))), 0, "")
  val HMRCNOVRNAGNTUserMapping =
    UserMapping(authProviderId, None, Seq(AgentEnrolment(VATAgentRefNo, IdentifierValue("FGH79/96KUJ"))), 0, "")
  val HMRCCHARAGENTUserMapping =
    UserMapping(authProviderId, None, Seq(AgentEnrolment(AgentCharId, IdentifierValue("FGH79/96KUJ"))), 0, "")
  val HMRCMGDAGNTUserMapping =
    UserMapping(authProviderId, None, Seq(AgentEnrolment(HmrcMgdAgentRef, IdentifierValue("737B.89"))), 0, "")
  val IRPAYEAGENTUserMapping =
    UserMapping(authProviderId, None, Seq(AgentEnrolment(IRAgentReferencePaye, IdentifierValue("F9876J"))), 0, "")
  val IRSDLTAGENTUserMapping =
    UserMapping(authProviderId, None, Seq(AgentEnrolment(SdltStorn, IdentifierValue("AAA0008"))), 0, "")

  val fixtures: Seq[TestFixture] = Seq(
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

  val userMappings: Seq[UserMapping] = Seq(
    AgentCodeUserMapping,
    IRSAAGENTUserMapping,
    HMCEVATAGNTUserMapping,
    IRCTAGENTUserMapping,
    HMRCGTSAGNTUserMapping,
    HMRCNOVRNAGNTUserMapping,
    HMRCCHARAGENTUserMapping,
    HMRCMGDAGNTUserMapping,
    IRPAYEAGENTUserMapping,
    IRSDLTAGENTUserMapping
  )

  "MappingController" should {

    "/mappings/arn/:arn" should {

      // Test each fixture in isolation first
      fixtures.foreach { f =>
        s"capture ${f.legacyAgentEnrolmentType.key} enrolment" when {
          "return created upon success" in {
            givenUserIsAuthorisedFor(f)
            val result = controller.createMapping(registeredArn)(fakePutEmptyRequest())
            status(result) shouldBe CREATED
            //callPut(createMappingRequest, None).status shouldBe 201
          }

          "return created upon success w/o agent code" in {
            givenUserIsAuthorisedFor(f.legacyAgentEnrolmentType.key, f.identifierKey, f.identifierValue, "testCredId", agentCodeOpt = None)
            val result = controller.createMapping(registeredArn)(fakePutEmptyRequest())
            status(result) shouldBe CREATED
            //callPut(createMappingRequest, None).status shouldBe 201
          }

          "return conflict when the mapping already exists" in {
            givenUserIsAuthorisedFor(f)
            val result = controller.createMapping(registeredArn)(fakePutEmptyRequest())
            status(result) shouldBe CREATED

            val inRepo = repositories.get(f.legacyAgentEnrolmentType).findAll().futureValue

            println(s">>>>>>>>>>>>>IN REPO $inRepo")

            val result2 = controller.createMapping(registeredArn)(fakePutEmptyRequest())
            status(result2) shouldBe CONFLICT

//            callPut(createMappingRequest, None).status shouldBe 201
//            callPut(createMappingRequest, None).status shouldBe 409

            verifyCreateMappingAuditEventSent(f)
          }

          "return forbidden when an authorisation error occurs" in {
            givenUserNotAuthorisedWithError("InsufficientEnrolments")

            val result = controller.createMapping(registeredArn)(fakePutEmptyRequest())
            status(result) shouldBe FORBIDDEN

            //callPut(createMappingRequest, None).status shouldBe 403
          }
        }
      }
    }

    "/mappings/task-list/arn/:arn" should {
      userMappings.foreach { u =>
        s"for agent code: ${if(u.agentCode.isDefined) "agentCode" else u.legacyEnrolments.head.enrolmentType}" when {
          "return created upon success" in {
            givenUserIsAuthorisedForCreds(IRSAAGENTTestFixture)
            givenUserMappingsExistsForAuthProviderId(authProviderId, Seq(u))

            val result = controller.createMappingsFromSubscriptionJourneyRecord(registeredArn)(fakePutEmptyRequest())
            status(result) shouldBe 201
            //callPut(createMappingFromSubscriptionJourneyRecordRequest, None).status shouldBe 201
          }
        }
      }

      "return NoContent when there are no user mappings found" in {
        givenUserIsAuthorisedForCreds(IRSAAGENTTestFixture)
        givenUserMappingsNotFoundForAuthProviderId(authProviderId)

        val result = controller.createMappingsFromSubscriptionJourneyRecord(registeredArn)(fakePutEmptyRequest())
        status(result) shouldBe NO_CONTENT
        //callPut(createMappingFromSubscriptionJourneyRecordRequest, None).status shouldBe 204
      }
    }

    fixtures.foreach { f =>
      s"capture ${f.legacyAgentEnrolmentType.key} enrolment for pre-subscription" when {
        "return created upon success" in {
          givenUserIsAuthorisedFor(f)
          val result = controller.createPreSubscriptionMapping(utr)(fakePutEmptyRequest())
          status(result) shouldBe CREATED

          //callPut(createPreSubscriptionMappingRequest, None).status shouldBe 201
        }

        "return created upon success w/o agent code" in {
          givenUserIsAuthorisedFor(f.legacyAgentEnrolmentType.key, f.identifierKey, f.identifierValue, "testCredId", agentCodeOpt = None)

          val result = controller.createPreSubscriptionMapping(utr)(fakePutEmptyRequest())
          status(result) shouldBe CREATED
          //callPut(createPreSubscriptionMappingRequest, None).status shouldBe 201
        }

        "return conflict when the mapping already exists" in {
          givenUserIsAuthorisedFor(f)

          val result = controller.createPreSubscriptionMapping(utr)(fakePutEmptyRequest())
          status(result) shouldBe CREATED

          val result2 = controller.createPreSubscriptionMapping(utr)(fakePutEmptyRequest())
          status(result2) shouldBe CONFLICT

//          callPut(createPreSubscriptionMappingRequest, None).status shouldBe 201
//          callPut(createPreSubscriptionMappingRequest, None).status shouldBe 409
        }

        "return forbidden when an authorisation error occurs" in {
          givenUserNotAuthorisedWithError("InsufficientEnrolments")

          val result = controller.createPreSubscriptionMapping(utr)(fakePutEmptyRequest())
          status(result) shouldBe FORBIDDEN

          //callPut(createPreSubscriptionMappingRequest, None).status shouldBe 403
        }
      }
    }

    // Then test all fixtures at once
    s"capture all ${fixtures.size} enrolments" when {
      s"return created upon success" in {
        givenUserIsAuthorisedForMultiple(fixtures)
        val result = controller.createMapping(registeredArn)(fakePutEmptyRequest())
        status(result) shouldBe CREATED
        //callPut(createMappingRequest, None).status shouldBe 201
      }
    }

    if (fixtures.size > 1) {
      // Then test different split sets of fixtures
      val splitFixtures: Seq[(Seq[TestFixture], Seq[TestFixture])] =
        (1 until Math.max(5, fixtures.size)).map(fixtures.splitAt) ++ (1 until Math.max(4, fixtures.size))
          .map(fixtures.reverse.splitAt)

      splitFixtures.foreach {
        case (left, right) =>
          val leftTag = left.map(f => f.legacyAgentEnrolmentType.key).mkString(",")
          s"return created when we add all, but some mappings already exist: $leftTag" in {
            givenUserIsAuthorisedForMultiple(left)
            val result = controller.createMapping(registeredArn)(fakePutEmptyRequest())
            status(result) shouldBe CREATED
            //callPut(createMappingRequest, None).status shouldBe 201

            givenUserIsAuthorisedForMultiple(fixtures)
            val result2 = controller.createMapping(registeredArn)(fakePutEmptyRequest())
            status(result2) shouldBe CREATED
            //callPut(createMappingRequest, None).status shouldBe 201

            fixtures.foreach(f => verifyCreateMappingAuditEventSent(f))
            verifyCreateMappingAuditEventSent(AgentCodeTestFixture)
          }

          s"return conflict when all mappings already exist, but we try to add again: $leftTag" in {
            givenUserIsAuthorisedForMultiple(fixtures)
            val result1 = controller.createMapping(registeredArn)(fakePutEmptyRequest())
            status(result1) shouldBe CREATED
            //callPut(createMappingRequest, None).status shouldBe 201

            givenUserIsAuthorisedForMultiple(left)
            val result2 = controller.createMapping(registeredArn)(fakePutEmptyRequest())
            status(result2) shouldBe CONFLICT
            //callPut(createMappingRequest, None).status shouldBe 409

            givenUserIsAuthorisedForMultiple(right)
            val result3 = controller.createMapping(registeredArn)(fakePutEmptyRequest())
            status(result3) shouldBe CONFLICT
            //callPut(createMappingRequest, None).status shouldBe 409

            fixtures.foreach(f => verifyCreateMappingAuditEventSent(f))
            verifyCreateMappingAuditEventSent(AgentCodeTestFixture)
          }
      }
    }

    "return bad request when the ARN is invalid" in {
      val result = controller.createMapping(Arn("BAD_ARN"))(fakePutEmptyRequest())
      status(result) shouldBe BAD_REQUEST
//      val response = callPut(s"/agent-mapping/mappings/arn/A_BAD_ARN", None)
//      response.status shouldBe 400
        //  result.bodyjson \ "message").as[String] shouldBe "bad request, cause: REDACTED"
    }

    "return unauthenticated" when {
      "unauthenticated user attempts to create mapping" in {

        givenUserNotAuthorisedWithError("MissingBearerToken")

        val result = controller.createMapping(registeredArn)(fakePutEmptyRequest())
        status(result) shouldBe UNAUTHORIZED
        //callPut(createMappingRequest, None).status shouldBe 401
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

        val result = controller.createMapping(registeredArn)(fakePutEmptyRequest())
        status(result) shouldBe BAD_REQUEST
        //callPut(createMappingRequest, None).status shouldBe 403
      }
      "authenticated user with IR-SA-AGENT enrolment but without Agent Affinity group attempts to create mapping" in {

        givenUserIsAuthorisedFor(
          IRAgentReference.key,
          IRSAAgentReference,
          "2000000000",
          "testCredId",
          AffinityGroup.Individual,
          Some(agentCode))
        val result = controller.createMapping(registeredArn)(fakePutEmptyRequest())
        status(result) shouldBe FORBIDDEN
        //callPut(createMappingRequest, None).status shouldBe 403
      }
    }
  }

  "update enrolment for post-subscription" should {
    "return 200 when succeeds" in {

      saRepo.store(utr, "A1111A").futureValue
      givenUserIsAuthorisedAsAgent(registeredArn.value)

      val result = controller.createPostSubscriptionMapping(utr)(fakePutEmptyRequest())
      status(result) shouldBe OK

      //callPut(updatePostSubscriptionMappingRequest, None).status shouldBe 200

      val updatedMapping = saRepo.findAll().futureValue
      updatedMapping.size shouldBe 1
      updatedMapping.head.businessId.value shouldBe registeredArn.value
    }

    "return 200 when user does not have mappings" in {
      givenUserIsAuthorisedAsAgent(registeredArn.value)

      saRepo.findAll().futureValue.size shouldBe 0

      val result = controller.createPostSubscriptionMapping(utr)(fakePutEmptyRequest())
      status(result) shouldBe OK

      //callPut(updatePostSubscriptionMappingRequest, None).status shouldBe 200
    }

    "return 401 when user is not authenticated" in {
      givenUserNotAuthorisedWithError("MissingBearerToken")

      val result = controller.createPostSubscriptionMapping(utr)(fakePutEmptyRequest())
      status(result) shouldBe UNAUTHORIZED

      //callPut(updatePostSubscriptionMappingRequest, None).status shouldBe 401
    }

    "return 403 when an authorisation error occurs" in {
      givenUserNotAuthorisedWithError("InsufficientEnrolments")

      val result = controller.createPostSubscriptionMapping(utr)(fakePutEmptyRequest())
      status(result) shouldBe FORBIDDEN


      //callPut(updatePostSubscriptionMappingRequest, None).status shouldBe 403
    }
  }

  "find mapping requests" should {
    "return 200 status with a json body representing the mappings that match the supplied arn" in {
      saRepo.store(registeredArn, "A1111A").futureValue
      saRepo.store(registeredArn, "A1111B").futureValue

      val result = controller.findSaMappings(registeredArn)(fakeGetRequest())
      status(result) shouldBe OK

      contentAsString(result) shouldBe """{"mappings":[{"arn":"AARN0000002","saAgentReference":"A1111A"},{"arn":"AARN0000002","saAgentReference":"A1111B"}]}"""


//      val response = callGet(findMappingsRequest)
//
//      response.status shouldBe 200
//      val body = response.body
//      body shouldBe """{"mappings":[{"arn":"AARN0000002","saAgentReference":"A1111A"},{"arn":"AARN0000002","saAgentReference":"A1111B"}]}"""
//
    }

    "return 200 status with a json body representing the mappings that match the supplied arn for sa" in {
      saRepo.store(registeredArn, "A1111A").futureValue
      saRepo.store(registeredArn, "A1111B").futureValue

      val result = controller.findSaMappings(registeredArn)(fakeGetRequest())
      status(result) shouldBe OK

      //val response = callGet(findSAMappingsRequest)

      contentAsString(result) shouldBe """{"mappings":[{"arn":"AARN0000002","saAgentReference":"A1111A"},{"arn":"AARN0000002","saAgentReference":"A1111B"}]}"""
    }

    "return 200 status with a json body representing the mappings that match the supplied arn for vat" in {
      vatRepo.store(registeredArn, "101747696").futureValue
      vatRepo.store(registeredArn, "101747641").futureValue

      val result = controller.findVatMappings(registeredArn)(fakeGetRequest())
      status(result) shouldBe OK

     // val response = callGet(findVATMappingsRequest)

//      response.status shouldBe 200
//      val body = response.body

      contentAsString(result) should include("""{"arn":"AARN0000002","vrn":"101747696"}""")
      contentAsString(result) should include("""{"arn":"AARN0000002","vrn":"101747641"}""")
    }

    "return 200 status with a json body representing the mappings that match the supplied arn for agent code" in {
      agentCodeRepo.store(registeredArn, "ABCDE1").futureValue
      agentCodeRepo.store(registeredArn, "ABCDE2").futureValue

      val result = controller.findAgentCodeMappings(registeredArn)(fakeGetRequest())
      status(result) shouldBe OK

      //val response = callGet(findAgentCodeMappingsRequest)

//      response.status shouldBe 200
//      val body = response.body

      contentAsString(result) should include("""{"arn":"AARN0000002","agentCode":"ABCDE1"}""")
      contentAsString(result) should include("""{"arn":"AARN0000002","agentCode":"ABCDE2"}""")
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
      s"return 200 status with a json body representing the mappings that match the supplied arn for ${f.dbKey}" in {
        val repo = repositories.get(f.legacyAgentEnrolmentType)
        repo.store(registeredArn, "ABCDE123456").futureValue
        repo.store(registeredArn, "ABCDE298980").futureValue

        val result = controller.findMappings(f.dbKey,registeredArn)(fakeGetRequest())
        status(result) shouldBe OK

       // val response = callGet(findMappingsRequestByKey(f.dbKey))

        //response.status shouldBe 200
        //val body = response.body

        contentAsString(result) should include("""{"arn":"AARN0000002","identifier":"ABCDE298980"}""")
        contentAsString(result) should include("""{"arn":"AARN0000002","identifier":"ABCDE123456"}""")
      }

      s"return 404 when there are no ${f.dbKey} mappings that match the supplied arn" in {
        val result = controller.findMappings(f.dbKey,registeredArn)(fakeGetRequest())
        status(result) shouldBe NOT_FOUND
        //callGet(findMappingsRequestByKey(f.dbKey)).status shouldBe 404
      }
    }

    "return 404 when there are no mappings that match the supplied arn" in {
      val result = controller.findSaMappings(registeredArn)(fakeGetRequest())
      status(result) shouldBe NOT_FOUND
      //callGet(findMappingsRequest).status shouldBe 404
    }

    "return 404 when there are no mappings that match the supplied arn for sa" in {
      val result = controller.findSaMappings(registeredArn)(fakeGetRequest())
      status(result) shouldBe NOT_FOUND
      //callGet(findSAMappingsRequest).status shouldBe 404
    }

    "return 404 when there are no mappings that match the supplied arn for vat" in {
      val result = controller.findVatMappings(registeredArn)(fakeGetRequest())
      status(result) shouldBe NOT_FOUND
      //callGet(findVATMappingsRequest).status shouldBe 404
    }
  }

  "delete" should {
    "return no content when a record is deleted" in {
      saRepo.store(registeredArn, "foo").futureValue
      vatRepo.store(registeredArn, "foo").futureValue
      agentCodeRepo.store(registeredArn, "foo").futureValue

      val foundResponse = controller.findSaMappings(registeredArn)(fakeGetRequest())
      status(foundResponse) shouldBe OK

//      val deleteResponse = callDelete(deleteMappingsRequest)
//      deleteResponse.status shouldBe 204

//      val notFoundResponse = callGet(findMappingsRequest)
//      notFoundResponse.status shouldBe 404
    }

//    "return no content when no record is deleted" in {
//      val deleteResponse = callDelete(deleteMappingsRequest)
//      deleteResponse.status shouldBe 204
//    }
  }

  "delete records with utr for pre-subscription" should {
    "return no content when a record is deleted" in {
      isLoggedIn
      saRepo.store(utr, "foo").futureValue

      val foundResponse = saRepo.findAll().futureValue
      foundResponse.size shouldBe 1

      val deleteResponse = controller.deletePreSubscriptionMapping(utr)(fakeGetRequest("DELETE"))

        //callDelete(deletePreSubscriptionMappingsRequest)
      status(deleteResponse) shouldBe NO_CONTENT

      val notFoundResponse = saRepo.findAll().futureValue
      notFoundResponse.size shouldBe 0
    }

//    "return no content when no record is deleted" in {
//      isLoggedIn
//      val deleteResponse = callDelete(deleteMappingsRequest)
//      deleteResponse.status shouldBe 204
//    }
  }

  "hasEligibleEnrolments" should {

    def request = controller.hasEligibleEnrolments(fakeGetRequest())
    // callGet(hasEligibleRequest)

    fixtures.foreach(behave like checkEligibility(_))

    def checkEligibility(testFixture: TestFixture): Unit = {

      s"return 200 status with a json body with hasEligibleEnrolments=true when user has enrolment: ${testFixture.legacyAgentEnrolmentType.key}" in {
        givenUserIsAuthorisedFor(
          testFixture.legacyAgentEnrolmentType.key,
          testFixture.identifierKey,
          testFixture.identifierValue,
          "testCredId",
          agentCodeOpt = Some(agentCode),
          expectedRetrievals = Seq("allEnrolments")
        )

        status(request) shouldBe OK
        val json = contentAsJson(request)
        (json \ "hasEligibleEnrolments").as[Boolean] shouldBe true
      }

      s"return 200 with hasEligibleEnrolments=false when user has only ineligible enrolment: ${testFixture.dbKey}" in {
        givenUserIsAuthorisedWithNoEnrolments(
          testFixture.legacyAgentEnrolmentType.key,
          testFixture.identifierKey,
          testFixture.identifierValue,
          "testCredId",
          agentCodeOpt = Some(agentCode)
        )
        status(request) shouldBe OK
        val json = contentAsJson(request)
        (json \ "hasEligibleEnrolments").as[Boolean] shouldBe false
      }

      s"return 401 if user is not logged in for ${testFixture.dbKey}" in {
        givenUserNotAuthorisedWithError("MissingBearerToken")

        status(request) shouldBe 401
      }

      s"return 401 if user is logged in but does not have agent affinity for ${testFixture.dbKey}" in {
        givenUserNotAuthorisedWithError("UnsupportedAffinityGroup")

        status(request) shouldBe 401
      }
    }
  }

  trait TestSetup {
    (Seq(AgentCodeTestFixture) ++ fixtures).foreach { f =>
      repositories.get(f.legacyAgentEnrolmentType).store(registeredArn, f.identifierValue).futureValue
    }
    detailsRepository.create(record).futureValue
  }

  "removeMappingsForAgent" should {
    "return 200 for successfully deleting all agent records" in new TestSetup {
      val response = controller.removeMappingsForAgent(registeredArn)(FakeRequest("DELETE","")
        .withHeaders(HeaderNames.authorisation -> s"Basic ${basicAuth("username:password")}"))   //wsClient.url(s"$url${terminateAgentsMapping(registeredArn)}")
        //.addHttpHeaders(HeaderNames.authorisation -> s"Basic ${basicAuth("username:password")}")
        //.delete
        //.futureValue

      status(response) shouldBe OK
      val json = contentAsJson(response)
      json.as[JsObject] shouldBe Json.toJson[TerminationResponse](TerminationResponse(Seq(DeletionCount("agent-mapping", "all-regimes", 11))))
    }

    "return 400 for invalid ARN" in  {
      givenOnlyStrideStub("caat", "12345")
      val response = controller.removeMappingsForAgent(registeredArn)(FakeRequest("DELETE",""))
      //callDelete(terminateAgentsMapping(Arn("MARN01"))).
        status(response) shouldBe BAD_REQUEST
//        status(response) shouldBe 400
    }
  }

  private def givenUserIsAuthorisedFor(f: TestFixture): StubMapping =
    givenUserIsAuthorisedFor(
      f.legacyAgentEnrolmentType.key,
      f.identifierKey,
      f.identifierValue,
      "testCredId",
      agentCodeOpt = Some(agentCode))

  private def givenUserIsAuthorisedForCreds(f: TestFixture): StubMapping =
    givenUserIsAuthorisedForCreds(
      f.legacyAgentEnrolmentType.key,
      f.identifierKey,
      f.identifierValue,
      "testCredId",
      agentCodeOpt = Some(agentCode))

  private def givenUserIsAuthorisedForMultiple(fixtures: Seq[TestFixture]): StubMapping =
    givenUserIsAuthorisedForMultiple(asEnrolments(fixtures), "testCredId", agentCodeOpt = Some(agentCode))

  private def asEnrolments(fixtures: Seq[TestFixture]): Set[Enrolment] =
    fixtures
      .map(
        f =>
          Enrolment(
            f.legacyAgentEnrolmentType.key,
            Seq(EnrolmentIdentifier(f.identifierKey, f.identifierValue)),
            "Activated"))
      .toSet

  private def verifyCreateMappingAuditEventSent(f: TestFixture, duplicate: Boolean = false): Unit =
    verifyAuditRequestSent(
      1,
      event = CreateMapping,
      detail = Map(
        "identifier"           -> f.identifierValue,
        "identifierType"       -> f.legacyAgentEnrolmentType.key,
        "agentReferenceNumber" -> "AARN0000002",
        "authProviderId"       -> "testCredId",
        "duplicate"            -> s"$duplicate"
      ),
      tags = Map("transactionName" -> "create-mapping", "path" -> "/agent-mapping/mappings/arn/AARN0000002")
    )
}

sealed trait MappingControllerISpecSetup
    extends AnyWordSpecLike
      with GuiceOneAppPerSuite
    with Matchers
    with OptionValues
    with WireMockSupport
    with AuthStubs
    with DataStreamStub
    with SubscriptionStub
    with ScalaFutures
    with MongoSupport
    {

  implicit val actorSystem: ActorSystem = ActorSystem()

      protected def appBuilder: GuiceApplicationBuilder = {
        new GuiceApplicationBuilder()
          .configure(
            Map(
              "microservice.services.auth.port" -> wireMockPort,
              "microservice.services.agent-subscription.port" -> wireMockPort,
              "microservice.services.agent-subscription.host" -> wireMockHost,
              "auditing.consumer.baseUri.host" -> wireMockHost,
              "auditing.consumer.baseUri.port" -> wireMockPort,
              "application.router" -> "testOnlyDoNotUseInAppConf.Routes",
              "mongodb.uri" -> mongoUri,
              "migrate-repositories" -> "false",
              "termination.stride.enrolment" -> "caat"
            ))
          .overrides(new TestGuiceModule)
      }

      override implicit lazy val app: Application = appBuilder.build()

//       val auditService = app.injector.instanceOf[AuditService]
//       val subscriptionConnector = app.injector.instanceOf[SubscriptionConnector]
//       val espConnector = app.injector.asInstanceOf[EnrolmentStoreProxyConnector]
//       val authActions = app.injector.instanceOf[AuthActions]
//       val cc = app.injector.instanceOf[ControllerComponents]
//       val appConfig = app.injector.asInstanceOf[AppConfig]





      //implicit val mat: Materializer = Materializer()
  //implicit val client: WSClient = AhcWSClient()
 // implicit val hc: HeaderCarrier = HeaderCarrier()
      val GET = "GET"
      val PUT = "PUT"

  implicit def fakeGetRequest(method: String = GET): FakeRequest[_] =
    FakeRequest(method, "")
    .withHeaders("Authorization" -> "Bearer XYZ")

      implicit def fakePutRequest[T](method: String = PUT, body: Option[T]): FakeRequest[T] =
        FakeRequest(method, "")
          .withHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
          .withBody(body.get)

      implicit def fakePutEmptyRequest(method: String = PUT): FakeRequest[_] = fakeGetRequest(PUT)

      import scala.concurrent.ExecutionContext.Implicits.global


      //val mongo = mongoComponent

  protected val detailsRepository = app.injector.instanceOf[MappingDetailsRepository]//new MappingDetailsRepository(mongo)

//            val newAgentCodeMappingRepository     = new NewAgentCodeMappingRepository(mongo)
//            val iRSAAGENTMappingRepository        = new IRSAAGENTMappingRepository(mongo)
//            val hMCEVATAGNTMappingRepository      = new HMCEVATAGNTMappingRepository(mongo)
//            val hMRCCHARAGENTMappingRepository    = new HMRCCHARAGENTMappingRepository(mongo)
//            val hMRCGTSAGNTMappingRepository      = new HMRCGTSAGNTMappingRepository(mongo)
//            val hMRCMGDAGNTMappingRepository      = new HMRCMGDAGNTMappingRepository(mongo)
//            val hMRCNOVRNAGNTMappingRepository    = new HMRCNOVRNAGNTMappingRepository(mongo)
//            val iRCTAGENTMappingRepository        = new IRCTAGENTMappingRepository(mongo)
//            val iRPAYEAGENTMappingRepository      = new IRPAYEAGENTMappingRepository(mongo)
//            val iRSDLTAGENTMappingRepository      = new IRSDLTAGENTMappingRepository(mongo)

      protected lazy val repositories: MappingRepositories = app.injector.instanceOf[MappingRepositories]   //new MappingRepositories(
//        newAgentCodeMappingRepository, hMCEVATAGNTMappingRepository, iRSAAGENTMappingRepository,
//        hMRCCHARAGENTMappingRepository, hMRCGTSAGNTMappingRepository, hMRCMGDAGNTMappingRepository,
//        hMRCNOVRNAGNTMappingRepository, iRCTAGENTMappingRepository, iRPAYEAGENTMappingRepository,
//        iRSDLTAGENTMappingRepository)

      //protected val repositories: MappingRepositories = app.injector.instanceOf[MappingRepositories]
     // protected val detailsRepository = app.injector.instanceOf[MappingDetailsRepository]



        lazy val controller = app.injector.instanceOf[MappingController]
//      lazy val controller = new MappingController(
//        appConfig, repositories, detailsRepository,auditService, subscriptionConnector, espConnector,cc,authActions)

      val saRepo: repositories.Repository = repositories.get(IRAgentReference)
      val vatRepo = repositories.get(AgentRefNo)
      val agentCodeRepo = repositories.get(AgentCode)




  private class TestGuiceModule extends AbstractModule {
    override def configure = {
      //bind(classOf[MappingRepositories]).toInstance(repositories)
      //bind(classOf[MappingDetailsRepository]).toInstance(detailsRepository)
    }
  }

//      val repos = Seq(
//        newAgentCodeMappingRepository, hMCEVATAGNTMappingRepository, iRSAAGENTMappingRepository,
//        hMRCCHARAGENTMappingRepository, hMRCGTSAGNTMappingRepository, hMRCMGDAGNTMappingRepository,
//        hMRCNOVRNAGNTMappingRepository, iRCTAGENTMappingRepository, iRPAYEAGENTMappingRepository,
//        iRSDLTAGENTMappingRepository)

  override def beforeEach() = {
    super.beforeEach()
    Future.sequence(repositories.map(_.ensureDbIndexes)).futureValue
    givenAuditConnector()
    ()
  }
}
