/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.uk.gov.hmrc.agentmapping.controllers

import org.apache.pekko.actor.ActorSystem
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.google.inject.AbstractModule
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentmapping.audit.CreateMapping
import uk.gov.hmrc.agentmapping.controller.MappingController
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmapping.repository._
import test.uk.gov.hmrc.agentmapping.stubs.AuthStubs
import test.uk.gov.hmrc.agentmapping.stubs.DataStreamStub
import test.uk.gov.hmrc.agentmapping.stubs.SubscriptionStub
import test.uk.gov.hmrc.agentmapping.support.WireMockSupport
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.auth.core.EnrolmentIdentifier
import uk.gov.hmrc.domain
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport

import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.concurrent.Future

class MappingControllerISpec
extends MappingControllerISpecSetup
with ScalaFutures {

  val registeredArn: Arn = Arn("AARN0000002")

  private val utr = Utr("2000000000")
  private val agentCode = "TZRXXV"

  val IRSAAgentReference = "IRAgentReference"
  val AgentReferenceNo = "AgentRefNo"
  val authProviderId = AuthProviderId("testCredId")

  private val ggTag = GGTag("1234")
  private val count = 10
  private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
  private val dateTime: LocalDateTime = LocalDateTime.parse("2019-10-10 12:00", dateTimeFormatter)
  private val mappingDisplayDetails = MappingDetails(
    authProviderId,
    ggTag,
    count,
    dateTime
  )

  private val record = MappingDetailsRepositoryRecord(registeredArn, Seq(mappingDisplayDetails))

  val url = s"http://localhost:$port"
  val wsClient = app.injector.instanceOf[WSClient]

  def callPost(
    path: String,
    body: JsValue
  ): WSResponse =
    wsClient
      .url(s"$url$path")
      .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
      .post(body)
      .futureValue

  def callGet(path: String): WSResponse =
    wsClient
      .url(s"$url$path")
      .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
      .get()
      .futureValue

  def callDelete(path: String): WSResponse =
    wsClient
      .url(s"$url$path")
      .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
      .delete()
      .futureValue

  def callPut(
    path: String,
    body: Option[String]
  ): WSResponse =
    if (body.isDefined) {
      wsClient
        .url(s"$url$path")
        .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
        .put(body.get)
        .futureValue
    }
    else {
      wsClient
        .url(s"$url$path")
        .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
        .execute("PUT")
        .futureValue
    }

  def basicAuth(string: String): String = Base64.getEncoder.encodeToString(string.getBytes(UTF_8))

  val hasEligibleRequest: String = s"/agent-mapping/mappings/eligibility"

  val createMappingRequest: String = s"/agent-mapping/mappings/arn/${registeredArn.value}"

  val createMappingFromSubscriptionJourneyRecordRequest: String = s"/agent-mapping/mappings/task-list/arn/${registeredArn.value}"

  val createPreSubscriptionMappingRequest: String = s"/agent-mapping/mappings/pre-subscription/utr/${utr.value}"

  val updatePostSubscriptionMappingRequest: String = s"/agent-mapping/mappings/post-subscription/utr/${utr.value}"

  val findMappingsRequest: String = s"/agent-mapping/mappings/${registeredArn.value}"

  val findSAMappingsRequest: String = s"/agent-mapping/mappings/sa/${registeredArn.value}"

  val findVATMappingsRequest: String = s"/agent-mapping/mappings/vat/${registeredArn.value}"

  val findAgentCodeMappingsRequest: String = s"/agent-mapping/mappings/agentcode/${registeredArn.value}"

  def findMappingsRequestByKey(key: String): String = s"/agent-mapping/mappings/key/$key/arn/${registeredArn.value}"

  val deleteMappingsRequest: String = s"/agent-mapping/test-only/mappings/${registeredArn.value}"

  val deletePreSubscriptionMappingsRequest: String = s"/agent-mapping/mappings/pre-subscription/utr/${utr.value}"

  def terminateAgentsMapping(arn: Arn): String = s"/agent-mapping/agent/${arn.value}/terminate"

  case class TestFixture(
    legacyAgentEnrolmentType: LegacyAgentEnrolmentType,
    identifierKey: String,
    identifierValue: String
  ) {
    val dbKey: String = legacyAgentEnrolmentType.dbKey
  }

  val AgentCodeTestFixture = TestFixture(
    AgentCode,
    "AgentCode",
    agentCode
  )
  val IRSAAGENTTestFixture = TestFixture(
    IRAgentReference,
    IRSAAgentReference,
    "A1111A"
  )
  val HMCEVATAGNTTestFixture = TestFixture(
    AgentRefNo,
    AgentReferenceNo,
    "101747696"
  )
  val IRCTAGENTTestFixture = TestFixture(
    IRAgentReferenceCt,
    IRSAAgentReference,
    "B2121C"
  )
  val HMRCGTSAGNTTestFixture = TestFixture(
    HmrcGtsAgentRef,
    "HMRCGTSAGENTREF",
    "AB8964622K"
  )
  val HMRCNOVRNAGNTTestFixture = TestFixture(
    VATAgentRefNo,
    "VATAgentRefNo",
    "FGH79/96KUJ"
  )
  val HMRCCHARAGENTTestFixture = TestFixture(
    AgentCharId,
    "AGENTCHARID",
    "FGH79/96KUJ"
  )
  val HMRCMGDAGNTTestFixture = TestFixture(
    HmrcMgdAgentRef,
    "HMRCMGDAGENTREF",
    "737B.89"
  )
  val IRPAYEAGENTTestFixture = TestFixture(
    IRAgentReferencePaye,
    IRSAAgentReference,
    "F9876J"
  )
  val IRSDLTAGENTTestFixture = TestFixture(
    SdltStorn,
    "STORN",
    "AAA0008"
  )

  val AgentCodeUserMapping = UserMapping(
    authProviderId,
    Some(domain.AgentCode("agent-code")),
    Seq.empty,
    0,
    ""
  )
  val IRSAAGENTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(IRAgentReference, IdentifierValue("A1111A"))),
    0,
    ""
  )
  val HMCEVATAGNTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(AgentRefNo, IdentifierValue("101747696"))),
    0,
    ""
  )
  val IRCTAGENTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(IRAgentReferenceCt, IdentifierValue("B2121C"))),
    0,
    ""
  )
  val HMRCGTSAGNTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(HmrcGtsAgentRef, IdentifierValue("AB8964622K"))),
    0,
    ""
  )
  val HMRCNOVRNAGNTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(VATAgentRefNo, IdentifierValue("FGH79/96KUJ"))),
    0,
    ""
  )
  val HMRCCHARAGENTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(AgentCharId, IdentifierValue("FGH79/96KUJ"))),
    0,
    ""
  )
  val HMRCMGDAGNTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(HmrcMgdAgentRef, IdentifierValue("737B.89"))),
    0,
    ""
  )
  val IRPAYEAGENTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(IRAgentReferencePaye, IdentifierValue("F9876J"))),
    0,
    ""
  )
  val IRSDLTAGENTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(SdltStorn, IdentifierValue("AAA0008"))),
    0,
    ""
  )

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
            callPut(createMappingRequest, None).status shouldBe 201

          }

          "return created upon success w/o agent code" in {
            givenUserIsAuthorisedFor(
              f.legacyAgentEnrolmentType.key,
              f.identifierKey,
              f.identifierValue,
              "testCredId",
              agentCodeOpt = None
            )
            callPut(createMappingRequest, None).status shouldBe 201
          }

          "return conflict when the mapping already exists" in {
            givenUserIsAuthorisedFor(f)

            callPut(createMappingRequest, None).status shouldBe 201
            callPut(createMappingRequest, None).status shouldBe 409

            verifyCreateMappingAuditEventSent(f)
          }

          "return forbidden when an authorisation error occurs" in {
            givenUserNotAuthorisedWithError("InsufficientEnrolments")

            callPut(createMappingRequest, None).status shouldBe 403
          }
        }
      }
    }

    "/mappings/task-list/arn/:arn" should {
      userMappings.foreach { u =>
        s"for agent code: ${if (u.agentCode.isDefined)
            "agentCode"
          else
            u.legacyEnrolments.head.enrolmentType}" when {
          "return created upon success" in {
            givenUserIsAuthorisedForCreds(IRSAAGENTTestFixture)
            givenUserMappingsExistsForAuthProviderId(authProviderId, Seq(u))

            callPut(createMappingFromSubscriptionJourneyRecordRequest, None).status shouldBe 201
          }
        }
      }

      "return NoContent when there are no user mappings found" in {
        givenUserIsAuthorisedForCreds(IRSAAGENTTestFixture)
        givenUserMappingsNotFoundForAuthProviderId(authProviderId)

        callPut(createMappingFromSubscriptionJourneyRecordRequest, None).status shouldBe 204
      }
    }

    fixtures.foreach { f =>
      s"capture ${f.legacyAgentEnrolmentType.key} enrolment for pre-subscription" when {
        "return created upon success" in {
          givenUserIsAuthorisedFor(f)

          callPut(createPreSubscriptionMappingRequest, None).status shouldBe 201
        }

        "return created upon success w/o agent code" in {
          givenUserIsAuthorisedFor(
            f.legacyAgentEnrolmentType.key,
            f.identifierKey,
            f.identifierValue,
            "testCredId",
            agentCodeOpt = None
          )

          callPut(createPreSubscriptionMappingRequest, None).status shouldBe 201
        }

        "return conflict when the mapping already exists" in {
          givenUserIsAuthorisedFor(f)

          callPut(createPreSubscriptionMappingRequest, None).status shouldBe 201
          callPut(createPreSubscriptionMappingRequest, None).status shouldBe 409
        }

        "return forbidden when an authorisation error occurs" in {
          givenUserNotAuthorisedWithError("InsufficientEnrolments")

          callPut(createPreSubscriptionMappingRequest, None).status shouldBe 403
        }
      }
    }

    // Then test all fixtures at once
    s"capture all ${fixtures.size} enrolments" when {
      s"return created upon success" in {
        givenUserIsAuthorisedForMultiple(fixtures)
        callPut(createMappingRequest, None).status shouldBe 201
        fixtures.foreach(f => verifyCreateMappingAuditEventSent(f))
      }
    }

    if (fixtures.size > 1) {
      // Then test different split sets of fixtures
      val splitFixtures: Seq[(Seq[TestFixture], Seq[TestFixture])] =
        (1 until Math.max(5, fixtures.size)).map(fixtures.splitAt) ++ (1 until Math.max(4, fixtures.size))
          .map(fixtures.reverse.splitAt)

      splitFixtures.foreach { case (left, right) =>
        val leftTag = left.map(f => f.legacyAgentEnrolmentType.key).mkString(",")
        s"return created when we add all, but some mappings already exist: $leftTag" in {
          givenUserIsAuthorisedForMultiple(left)
          callPut(createMappingRequest, None).status shouldBe 201

          givenUserIsAuthorisedForMultiple(fixtures)
          callPut(createMappingRequest, None).status shouldBe 201

          fixtures.foreach(f => verifyCreateMappingAuditEventSent(f))
          verifyCreateMappingAuditEventSent(AgentCodeTestFixture)
        }

        s"return conflict when all mappings already exist, but we try to add again: $leftTag" in {
          givenUserIsAuthorisedForMultiple(fixtures)
          callPut(createMappingRequest, None).status shouldBe 201

          givenUserIsAuthorisedForMultiple(left)
          callPut(createMappingRequest, None).status shouldBe 409

          givenUserIsAuthorisedForMultiple(right)
          callPut(createMappingRequest, None).status shouldBe 409

          fixtures.foreach(f => verifyCreateMappingAuditEventSent(f))
          verifyCreateMappingAuditEventSent(AgentCodeTestFixture)
        }
      }
    }

    "return bad request when the ARN is invalid" in {
      val response = callPut(s"/agent-mapping/mappings/arn/A_BAD_ARN", None)
      response.status shouldBe 400
      // (response.json \ "message").as[String] shouldBe "bad request, cause: REDACTED"
    }

    "return unauthenticated" when {
      "unauthenticated user attempts to create mapping" in {

        givenUserNotAuthorisedWithError("MissingBearerToken")

        callPut(createMappingRequest, None).status shouldBe 401
      }
    }

    "return forbidden" when {
      "user with Agent affinity group and HMRC-AS-AGENT enrolment attempts to create mapping" in {

        givenUserIsAuthorisedFor(
          "HMRC-AS-AGENT",
          "AgentReferenceNumber",
          "TARN000003",
          "testCredId",
          agentCodeOpt = Some(agentCode)
        )

        callPut(createMappingRequest, None).status shouldBe 403
      }
      "authenticated user with IR-SA-AGENT enrolment but without Agent Affinity group attempts to create mapping" in {

        givenUserIsAuthorisedFor(
          IRAgentReference.key,
          IRSAAgentReference,
          "2000000000",
          "testCredId",
          AffinityGroup.Individual,
          Some(agentCode)
        )
        callPut(createMappingRequest, None).status shouldBe 403
      }
    }
  }

  "update enrolment for post-subscription" should {
    "return 200 when succeeds" in {

      saRepo.store(utr, "A1111A").futureValue
      givenUserIsAuthorisedAsAgent(registeredArn.value)

      callPut(updatePostSubscriptionMappingRequest, None).status shouldBe 200

      val updatedMapping = saRepo.findAll().futureValue
      updatedMapping.size shouldBe 1
      updatedMapping.head.businessId.value shouldBe registeredArn.value
    }

    "return 200 when user does not have mappings" in {
      givenUserIsAuthorisedAsAgent(registeredArn.value)

      saRepo.findAll().futureValue.size shouldBe 0

      callPut(updatePostSubscriptionMappingRequest, None).status shouldBe 200
    }

    "return 401 when user is not authenticated" in {
      givenUserNotAuthorisedWithError("MissingBearerToken")

      callPut(updatePostSubscriptionMappingRequest, None).status shouldBe 401
    }

    "return 403 when an authorisation error occurs" in {
      givenUserNotAuthorisedWithError("InsufficientEnrolments")

      callPut(updatePostSubscriptionMappingRequest, None).status shouldBe 403
    }
  }

  "find mapping requests" should {
    "return 200 status with a json body representing the mappings that match the supplied arn" in {
      saRepo.store(registeredArn, "A1111A").futureValue
      saRepo.store(registeredArn, "A1111B").futureValue

      val response = callGet(findMappingsRequest)

      response.status shouldBe 200
      val body = response.body
      body shouldBe """{"mappings":[{"arn":"AARN0000002","saAgentReference":"A1111A"},{"arn":"AARN0000002","saAgentReference":"A1111B"}]}"""

    }

    "return 200 status with a json body representing the mappings that match the supplied arn for sa" in {
      saRepo.store(registeredArn, "A1111A").futureValue
      saRepo.store(registeredArn, "A1111B").futureValue

      val response = callGet(findSAMappingsRequest)

      response.body shouldBe """{"mappings":[{"arn":"AARN0000002","saAgentReference":"A1111A"},{"arn":"AARN0000002","saAgentReference":"A1111B"}]}"""
    }

    "return 200 status with a json body representing the mappings that match the supplied arn for agent code" in {
      agentCodeRepo.store(registeredArn, "ABCDE1").futureValue
      agentCodeRepo.store(registeredArn, "ABCDE2").futureValue

      val response = callGet(findAgentCodeMappingsRequest)

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
      s"return 200 status with a json body representing the mappings that match the supplied arn for ${f.dbKey}" in {
        val repo = repositories.get(f.legacyAgentEnrolmentType)
        repo.store(registeredArn, "ABCDE123456").futureValue
        repo.store(registeredArn, "ABCDE298980").futureValue

        val response = callGet(findMappingsRequestByKey(f.dbKey))

        response.status shouldBe 200
        val body = response.body

        body should include("""{"arn":"AARN0000002","identifier":"ABCDE298980"}""")
        body should include("""{"arn":"AARN0000002","identifier":"ABCDE123456"}""")
      }

      s"return 404 when there are no ${f.dbKey} mappings that match the supplied arn" in {
        callGet(findMappingsRequestByKey(f.dbKey)).status shouldBe 404
      }
    }

    "return 404 when there are no mappings that match the supplied arn" in {
      callGet(findMappingsRequest).status shouldBe 404
    }

    "return 404 when there are no mappings that match the supplied arn for sa" in {
      callGet(findSAMappingsRequest).status shouldBe 404
    }

    "return 404 when there are no mappings that match the supplied arn for vat" in {
      callGet(findVATMappingsRequest).status shouldBe 404
    }
  }

  "delete" should {
    "return no content when a record is deleted" in {
      saRepo.store(registeredArn, "foo").futureValue
      vatRepo.store(registeredArn, "foo").futureValue
      agentCodeRepo.store(registeredArn, "foo").futureValue

      val deleteResponse = callDelete(deleteMappingsRequest)
      deleteResponse.status shouldBe 204

      val notFoundResponse = callGet(findMappingsRequest)
      notFoundResponse.status shouldBe 404
    }

    "return no content when no record is deleted" in {
      val deleteResponse = callDelete(deleteMappingsRequest)
      deleteResponse.status shouldBe 204
    }
  }

  "delete records with utr for pre-subscription" should {
    "return no content when a record is deleted" in {
      isLoggedIn
      saRepo.store(utr, "foo").futureValue

      val foundResponse = saRepo.findAll().futureValue
      foundResponse.size shouldBe 1

      val deleteResponse = callDelete(deletePreSubscriptionMappingsRequest)

      deleteResponse.status shouldBe 204

      val notFoundResponse = saRepo.findAll().futureValue
      notFoundResponse.size shouldBe 0
    }

    "return no content when no record is deleted" in {
      isLoggedIn
      val deleteResponse = callDelete(deleteMappingsRequest)
      deleteResponse.status shouldBe 204
    }
  }

  "hasEligibleEnrolments" should {

    def request = callGet(hasEligibleRequest)

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

        request.status shouldBe 200
        val json = request.json
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
        request.status shouldBe 200
        val json = request.json
        (json \ "hasEligibleEnrolments").as[Boolean] shouldBe false
      }

      s"return 401 if user is not logged in for ${testFixture.dbKey}" in {
        givenUserNotAuthorisedWithError("MissingBearerToken")

        request.status shouldBe 401
      }

      s"return 401 if user is logged in but does not have agent affinity for ${testFixture.dbKey}" in {
        givenUserNotAuthorisedWithError("UnsupportedAffinityGroup")

        request.status shouldBe 401
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

      val response =
        wsClient
          .url(s"$url${terminateAgentsMapping(registeredArn)}")
          .addHttpHeaders(HeaderNames.authorisation -> s"Basic ${basicAuth("username:password")}")
          .delete()
          .futureValue

      response.status shouldBe 200
      val json = response.json
      json.as[JsObject] shouldBe Json.toJson[TerminationResponse](
        TerminationResponse(Seq(DeletionCount(
          "agent-mapping",
          "all-regimes",
          11
        )))
      )
    }

    "return 400 for invalid ARN" in {
      givenOnlyStrideStub("caat", "12345")
      val response = callDelete(terminateAgentsMapping(Arn("MARN01")))

      response.status shouldBe 400
    }
  }

  private def givenUserIsAuthorisedFor(f: TestFixture): StubMapping = givenUserIsAuthorisedFor(
    f.legacyAgentEnrolmentType.key,
    f.identifierKey,
    f.identifierValue,
    "testCredId",
    agentCodeOpt = Some(agentCode)
  )

  private def givenUserIsAuthorisedForCreds(f: TestFixture): StubMapping = givenUserIsAuthorisedForCreds(
    f.legacyAgentEnrolmentType.key,
    f.identifierKey,
    f.identifierValue,
    "testCredId",
    agentCodeOpt = Some(agentCode)
  )

  private def givenUserIsAuthorisedForMultiple(fixtures: Seq[TestFixture]): StubMapping = givenUserIsAuthorisedForMultiple(
    asEnrolments(fixtures),
    "testCredId",
    agentCodeOpt = Some(agentCode)
  )

  private def asEnrolments(fixtures: Seq[TestFixture]): Set[Enrolment] =
    fixtures
      .map(f =>
        Enrolment(
          f.legacyAgentEnrolmentType.key,
          Seq(EnrolmentIdentifier(f.identifierKey, f.identifierValue)),
          "Activated"
        )
      )
      .toSet

  private def verifyCreateMappingAuditEventSent(
    f: TestFixture,
    duplicate: Boolean = false
  ): Unit = verifyAuditRequestSent(
    1,
    event = CreateMapping,
    detail = Map(
      "identifier" -> f.identifierValue,
      "identifierType" -> f.legacyAgentEnrolmentType.key,
      "agentReferenceNumber" -> "AARN0000002",
      "authProviderId" -> "testCredId",
      "duplicate" -> s"$duplicate"
    ),
    tags = Map("transactionName" -> "create-mapping")
  )

}

sealed trait MappingControllerISpecSetup
extends AnyWordSpecLike
with GuiceOneServerPerSuite
with Matchers
with OptionValues
with WireMockSupport
with AuthStubs
with DataStreamStub
with SubscriptionStub
with ScalaFutures
with MongoSupport {

  implicit val actorSystem: ActorSystem = ActorSystem()

  protected def appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      Map(
        "microservice.services.auth.port" -> wireMockPort,
        "microservice.services.agent-subscription.port" -> wireMockPort,
        "microservice.services.agent-subscription.host" -> wireMockHost,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "application.router" -> "testOnlyDoNotUseInAppConf.Routes",
        "migrate-repositories" -> "false",
        "termination.stride.enrolment" -> "caat",
        "mongodb.uri" -> mongoUri
      )
    )
    .overrides(new TestGuiceModule)

  override implicit lazy val app: Application = appBuilder.build()

  protected lazy val detailsRepository = new MappingDetailsRepository(mongoComponent)

  protected lazy val repositories: MappingRepositories = app.injector.instanceOf[MappingRepositories]

  lazy val controller = app.injector.instanceOf[MappingController]

  lazy val saRepo = repositories.get(IRAgentReference)
  lazy val vatRepo = repositories.get(AgentRefNo)
  lazy val agentCodeRepo = repositories.get(AgentCode)

  private class TestGuiceModule
  extends AbstractModule {
    override def configure = {
      bind(classOf[MongoComponent]).toInstance(mongoComponent)
      bind(classOf[MappingDetailsRepository]).toInstance(detailsRepository)
    }
  }

  def deleteTestDataInAllCollections = Await.result(Future.sequence(repositories.map(coll => coll.deleteAll())), 20.seconds)

  override def commonStubs(): Unit = {
    givenAuditConnector
    ()
  }

  override def beforeEach() = {
    super.beforeEach()
    commonStubs()
    deleteTestDataInAllCollections
    ()
  }

  override def afterAll() = {
    deleteTestDataInAllCollections
    super.afterAll()
    ()
  }

}
