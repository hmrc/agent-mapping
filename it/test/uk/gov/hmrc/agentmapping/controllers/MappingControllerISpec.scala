/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentmapping.controllers

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentmapping.audit.AgentMappingEvent.CreateMapping
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolment
import uk.gov.hmrc.agentmapping.model.{Enrolment as ModelEnrolment, EnrolmentIdentifier as ModelEnrolmentIdentifier, *}
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.auth.core.EnrolmentIdentifier

import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate
import java.util.Base64

class MappingControllerISpec
extends MappingControllerISpecSetup
with ScalaFutures:

  val registeredArn: Arn = Arn("AARN0000002")

  private val agentCode = "TZRXXV"

  val IRSAAgentReference = "IRAgentReference"
  val AgentReferenceNo = "AgentRefNo"
  val authProviderId = AuthProviderId("testCredId")

  val url = s"http://localhost:$port"
  val wsClient: WSClient = app.injector.instanceOf[WSClient]

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

    if body.isDefined then
      wsClient
        .url(s"$url$path")
        .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
        .put(body.get)
        .futureValue
    else
      wsClient
        .url(s"$url$path")
        .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
        .execute("PUT")
        .futureValue
    end if

  end callPut

  def basicAuth(string: String): String = Base64.getEncoder.encodeToString(string.getBytes(UTF_8))

  val hasEligibleRequest: String = s"/agent-mapping/mappings/eligibility"

  val createMappingRequest: String = s"/agent-mapping/mappings/arn/${registeredArn.value}"

  val createAutoMappingRequest: String = s"/agent-mapping/mappings/auto-map/arn/${registeredArn.value}"

  val findSAMappingsRequest: String = s"/agent-mapping/mappings/sa/${registeredArn.value}"

  val findVATMappingsRequest: String = s"/agent-mapping/mappings/vat/${registeredArn.value}"

  val findAgentCodeMappingsRequest: String = s"/agent-mapping/mappings/agentcode/${registeredArn.value}"

  def findMappingsRequestByKey(key: String): String = s"/agent-mapping/mappings/key/$key/arn/${registeredArn.value}"

  val deleteMappingsRequest: String = s"/agent-mapping/test-only/mappings/${registeredArn.value}"

  def terminateAgentsMapping(arn: Arn): String = s"/agent-mapping/agent/${arn.value}/terminate"

  case class TestFixture(
    legacyAgentEnrolmentType: LegacyAgentEnrolment,
    identifierKey: String,
    identifierValue: String
  ) {
    val dbKey: String = legacyAgentEnrolmentType.getDataBaseKey
  }

  val AgentCodeTestFixture = TestFixture(
    LegacyAgentEnrolment.AgentCode,
    "AgentCode",
    agentCode
  )
  val IRSAAGENTTestFixture = TestFixture(
    LegacyAgentEnrolment.IRAgentReference,
    IRSAAgentReference,
    "A1111A"
  )
  val HMCEVATAGNTTestFixture = TestFixture(
    LegacyAgentEnrolment.AgentRefNo,
    AgentReferenceNo,
    "101747696"
  )
  val IRCTAGENTTestFixture = TestFixture(
    LegacyAgentEnrolment.IRAgentReferenceCt,
    IRSAAgentReference,
    "B2121C"
  )
  val HMRCGTSAGNTTestFixture = TestFixture(
    LegacyAgentEnrolment.HmrcGtsAgentRef,
    "HMRCGTSAGENTREF",
    "AB8964622K"
  )
  val HMRCNOVRNAGNTTestFixture = TestFixture(
    LegacyAgentEnrolment.VATAgentRefNo,
    "VATAgentRefNo",
    "FGH79/96KUJ"
  )
  val HMRCCHARAGENTTestFixture = TestFixture(
    LegacyAgentEnrolment.AgentCharId,
    "AGENTCHARID",
    "FGH79/96KUJ"
  )
  val HMRCMGDAGNTTestFixture = TestFixture(
    LegacyAgentEnrolment.HmrcMgdAgentRef,
    "HMRCMGDAGENTREF",
    "737B.89"
  )
  val IRPAYEAGENTTestFixture = TestFixture(
    LegacyAgentEnrolment.IRAgentReferencePaye,
    IRSAAgentReference,
    "F9876J"
  )
  val IRSDLTAGENTTestFixture = TestFixture(
    LegacyAgentEnrolment.SdltStorn,
    "STORN",
    "AAA0008"
  )

  val AgentCodeUserMapping = UserMapping(
    authProviderId,
    Some(uk.gov.hmrc.domain.AgentCode("agent-code")),
    Seq.empty,
    0,
    ""
  )
  val IRSAAGENTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(LegacyAgentEnrolment.IRAgentReference, IdentifierValue("A1111A"))),
    0,
    ""
  )
  val HMCEVATAGNTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(LegacyAgentEnrolment.AgentRefNo, IdentifierValue("101747696"))),
    0,
    ""
  )
  val IRCTAGENTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(LegacyAgentEnrolment.IRAgentReferenceCt, IdentifierValue("B2121C"))),
    0,
    ""
  )
  val HMRCGTSAGNTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(LegacyAgentEnrolment.HmrcGtsAgentRef, IdentifierValue("AB8964622K"))),
    0,
    ""
  )
  val HMRCNOVRNAGNTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(LegacyAgentEnrolment.VATAgentRefNo, IdentifierValue("FGH79/96KUJ"))),
    0,
    ""
  )
  val HMRCCHARAGENTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(LegacyAgentEnrolment.AgentCharId, IdentifierValue("FGH79/96KUJ"))),
    0,
    ""
  )
  val HMRCMGDAGNTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(LegacyAgentEnrolment.HmrcMgdAgentRef, IdentifierValue("737B.89"))),
    0,
    ""
  )
  val IRPAYEAGENTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(LegacyAgentEnrolment.IRAgentReferencePaye, IdentifierValue("F9876J"))),
    0,
    ""
  )
  val IRSDLTAGENTUserMapping = UserMapping(
    authProviderId,
    None,
    Seq(AgentEnrolment(LegacyAgentEnrolment.SdltStorn, IdentifierValue("AAA0008"))),
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

            verifyCreateMappingAuditEventSent(f)
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

            verifyCreateMappingAuditEventSent(f)
          }

          "return conflict when the mapping already exists" in {
            givenUserIsAuthorisedFor(f)

            callPut(createMappingRequest, None).status shouldBe 201
            callPut(createMappingRequest, None).status shouldBe 409

            verifyCreateMappingAuditEventSent(f)
            verifyCreateMappingAuditEventSent(f, duplicate = true)
          }

          "return forbidden when an authorisation error occurs" in {
            givenUserNotAuthorisedWithError("InsufficientEnrolments")

            callPut(createMappingRequest, None).status shouldBe 403
          }
        }
      }
    }

    "/mappings/auto-map/arn/:arn" should {

      "return Created when principal enrolments exist" in {

        val arnValue = registeredArn.value
        val groupId = "group-123"
        val providerId = "testCredId"
        val identifierValue = "1234567890"

        givenAuthorisedAsAgentWithGroup(
          arn = arnValue,
          groupId = groupId,
          providerId = providerId
        )

        givenPrincipalEnrolmentsExist(
          groupId = groupId,
          enrolments = List(
            ModelEnrolment(
              LegacyAgentEnrolment.AgentCode.key,
              "Activated",
              Seq(ModelEnrolmentIdentifier("UTR", identifierValue))
            )
          )
        )

        callPut(createAutoMappingRequest, None).status shouldBe 201
      }
      "return NoContent when no principal enrolments exist" in {

        val arnValue = registeredArn.value
        val groupId = "group-123"
        val providerId = "testCredId"

        givenAuthorisedAsAgentWithGroup(
          arn = arnValue,
          groupId = groupId,
          providerId = providerId
        )

        givenPrincipalEnrolmentsExist(
          groupId = groupId,
          enrolments = Seq.empty
        )

        callPut(createAutoMappingRequest, None).status shouldBe 204
      }
      "return NoContent when principal enrolments are not Activated" in {

        val arnValue = registeredArn.value
        val groupId = "group-123"
        val providerId = "testCredId"

        givenAuthorisedAsAgentWithGroup(
          arn = arnValue,
          groupId = groupId,
          providerId = providerId
        )

        givenPrincipalEnrolmentsExist(
          groupId = groupId,
          enrolments = List(
            ModelEnrolment(
              LegacyAgentEnrolment.AgentCode.key,
              "Pending",
              Seq(ModelEnrolmentIdentifier("UTR", "1234567890"))
            )
          )
        )

        callPut(createAutoMappingRequest, None).status shouldBe 204
      }

      "return Forbidden when ARN does not match authorised ARN" in {

        val groupId = "group-123"
        val providerId = "testCredId"

        givenAuthorisedAsAgentWithGroup(
          arn = "AARN9999999",
          groupId = groupId,
          providerId = providerId
        )

        givenPrincipalEnrolmentsExist(
          groupId = groupId,
          enrolments = List(
            ModelEnrolment(
              LegacyAgentEnrolment.AgentCode.key,
              "Activated",
              Seq(ModelEnrolmentIdentifier("UTR", "1234567890"))
            )
          )
        )

        givenUserIsSubscribedAgentWithGroup(authArn = Arn("AARN9999999"))

        callPut(createAutoMappingRequest, None).status shouldBe 403
      }
      "return Conflict when mappings already exist" in {

        val arnValue = registeredArn.value
        val groupId = "group-123"
        val providerId = "testCredId"
        val identifierValue = "1234567890"

        givenAuthorisedAsAgentWithGroup(
          arn = arnValue,
          groupId = groupId,
          providerId = providerId
        )

        givenPrincipalEnrolmentsExist(
          groupId = groupId,
          enrolments = List(
            ModelEnrolment(
              LegacyAgentEnrolment.AgentCode.key,
              "Activated",
              Seq(ModelEnrolmentIdentifier("UTR", identifierValue))
            )
          )
        )

        callPut(createAutoMappingRequest, None).status shouldBe 201

        callPut(createAutoMappingRequest, None).status shouldBe 409
      }
      "return Unauthorised when user not logged in" in {

        val groupId = "group-123"

        givenUserNotAuthorisedWithError("MissingBearerToken")

        givenPrincipalEnrolmentsExist(
          groupId = groupId,
          enrolments = List(
            ModelEnrolment(
              LegacyAgentEnrolment.AgentCode.key,
              "Activated",
              Seq(ModelEnrolmentIdentifier("UTR", "1234567890"))
            )
          )
        )

        callPut(createAutoMappingRequest, None).status shouldBe 401
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
      val splitFixtures: Seq[(Seq[TestFixture], Seq[TestFixture])] = (1 until Math.max(5, fixtures.size)).map(fixtures.splitAt) ++
        (1 until Math.max(4, fixtures.size))
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
          LegacyAgentEnrolment.IRAgentReference.key,
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

  "find mapping requests" should {
    "return 200 status with a json body representing the mappings that match the supplied arn for sa" in {
      isLoggedIn
      saRepo.store(registeredArn, "A1111A").futureValue
      saRepo.store(registeredArn, "A1111B").futureValue

      val response = callGet(findSAMappingsRequest)

      response.status shouldBe 200
      response.json shouldBe Json.obj(
        "mappings" -> Json.arr(
          Json.obj(
            "arn" -> "AARN0000002",
            "saAgentReference" -> "A1111A",
            "created" -> LocalDate.now()
          ),
          Json.obj(
            "arn" -> "AARN0000002",
            "saAgentReference" -> "A1111B",
            "created" -> LocalDate.now()
          )
        )
      )
    }

    "return 200 status with a json body representing the mappings that match the supplied arn for agent code" in {
      isLoggedIn
      agentCodeRepo.store(registeredArn, "ABCDE1").futureValue
      agentCodeRepo.store(registeredArn, "ABCDE2").futureValue

      val response = callGet(findAgentCodeMappingsRequest)

      response.status shouldBe 200
      response.json shouldBe Json.obj(
        "mappings" -> Json.arr(
          Json.obj(
            "arn" -> "AARN0000002",
            "agentCode" -> "ABCDE1",
            "created" -> LocalDate.now()
          ),
          Json.obj(
            "arn" -> "AARN0000002",
            "agentCode" -> "ABCDE2",
            "created" -> LocalDate.now()
          )
        )
      )
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
        isLoggedIn
        val repo = repositories.get(f.legacyAgentEnrolmentType)
        repo.store(registeredArn, "ABCDE123456").futureValue
        repo.store(registeredArn, "ABCDE298980").futureValue

        val response = callGet(findMappingsRequestByKey(f.dbKey))

        response.status shouldBe 200
        response.json shouldBe Json.obj(
          "mappings" -> Json.arr(
            Json.obj(
              "arn" -> "AARN0000002",
              "identifier" -> "ABCDE123456",
              "created" -> LocalDate.now()
            ),
            Json.obj(
              "arn" -> "AARN0000002",
              "identifier" -> "ABCDE298980",
              "created" -> LocalDate.now()
            )
          )
        )
      }

      s"return 404 when there are no ${f.dbKey} mappings that match the supplied arn" in {
        isLoggedIn
        callGet(findMappingsRequestByKey(f.dbKey)).status shouldBe 404
      }
    }

    "return 404 when there are no mappings that match the supplied arn for sa" in {
      isLoggedIn
      callGet(findSAMappingsRequest).status shouldBe 404
    }

    "return 404 when there are no mappings that match the supplied arn for vat" in {
      isLoggedIn
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

      isLoggedIn
      val notFoundResponse = callGet(findSAMappingsRequest)
      notFoundResponse.status shouldBe 404
    }

    "return no content when no record is deleted" in {
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
  }

  private def givenUserIsAuthorisedFor(f: TestFixture): StubMapping =
    givenUserIsAuthorisedFor(
      f.legacyAgentEnrolmentType.key,
      f.identifierKey,
      f.identifierValue,
      "testCredId",
      agentCodeOpt = Some(agentCode)
    )
  end givenUserIsAuthorisedFor

  private def givenUserIsSubscribedAgentWithGroup(
    authArn: Arn = registeredArn,
    groupId: String = "group-123",
    providerId: String = "testCredId"
  ): StubMapping =
    givenAuthorisedAsAgentWithGroup(
      arn = authArn.value,
      groupId = groupId,
      providerId = providerId
    )
  end givenUserIsSubscribedAgentWithGroup

  private def givenUserIsAuthorisedForMultiple(fixtures: Seq[TestFixture]): StubMapping =
    givenUserIsAuthorisedForMultiple(
      asEnrolments(fixtures),
      "testCredId",
      agentCodeOpt = Some(agentCode)
    )
  end givenUserIsAuthorisedForMultiple

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
  end asEnrolments

  private def verifyCreateMappingAuditEventSent(
    f: TestFixture,
    duplicate: Boolean = false
  ): Unit =
    verifyAuditRequestSent(
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
  end verifyCreateMappingAuditEventSent

  override val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

end MappingControllerISpec
