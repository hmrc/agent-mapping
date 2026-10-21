/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.concurrent.ScalaFutures
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.model.Enrolment
import uk.gov.hmrc.agentmapping.model.EnrolmentIdentifier
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolmentType
import uk.gov.hmrc.agentmapping.stubs.AgentServicesAccountStubs
import uk.gov.hmrc.agentmapping.stubs.AuthStubs
import uk.gov.hmrc.agentmapping.stubs.EnrolmentStoreStubs
import uk.gov.hmrc.agentmapping.support.MongoSupport
import uk.gov.hmrc.agentmapping.support.ServerBaseISpec
import play.api.inject.bind

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class MappingStatusControllerISpec
extends ServerBaseISpec
with MongoSupport
with AuthStubs
with AgentServicesAccountStubs
with EnrolmentStoreStubs
with ScalaFutures:

  override val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  private val enrolmentType = LegacyAgentEnrolmentType.AgentRefNo
  private val identifierValue = "101747696"
  private val otherEnrolmentType = LegacyAgentEnrolmentType.IRAgentReference
  private val otherIdentifierValue = "AB1234"
  private val otherEnrolmentType2 = LegacyAgentEnrolmentType.AgentCharId
  private val otherIdentifierValue2 = "FGH79/96KUJ"
  private val arn = Arn("JARN1234567")
  private val arn2 = Arn("FARN1234567")

  private lazy val mandationDate = LocalDate.parse("2026-01-01")
  private lazy val afterMandationDate = mandationDate.plusDays(1)
  private lazy val beforeMandationDate = mandationDate.minusDays(1)
  private lazy val testGroupId = "testGroupId"
  private lazy val testCredId = "testCredId"

  private lazy val testClock = new MutableClock(afterMandationDate)

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder.overrides(bind[Clock].toInstance(testClock))

  override def beforeEach(): Unit =
    super.beforeEach()
    testClock.setDate(afterMandationDate)
  end beforeEach

  override def afterEach(): Unit =
    testClock.setDate(afterMandationDate)
    super.afterEach()
  end afterEach

  "Mapping status" when:
    "the user is not authorised" should:
      "return unauthorised" in:
        givenUserNotAuthorisedWithError("MissingBearerToken")

        val response = callGet(s"/mappings-status/enrolment/${enrolmentType.dbKey}/value/$identifierValue")

        response.status shouldBe 401

    "the authenticated user does not hold the requested enrolment" should:
      "return forbidden" in:
        givenUserIsAuthorisedFor(
          LegacyAgentEnrolmentType.IRAgentReference.key,
          LegacyAgentEnrolmentType.IRAgentReference.toString,
          "A1111A",
          ggCredId = testCredId,
          groupIdOpt = Some(testGroupId),
          agentCodeOpt = None,
          expectedRetrievals = Seq(
            "allEnrolments",
            "groupIdentifier",
            "optionalCredentials"
          )
        )

        val response = callGet(s"/mappings-status/enrolment/${enrolmentType.dbKey}/value/$identifierValue")

        response.status shouldBe 403

    "the identifier is mapped to a live ARN" should:
      "return no content when the ARN is not suspended" in:
        givenUserIsAuthorisedFor(
          enrolmentType.key,
          enrolmentType.toString,
          identifierValue,
          ggCredId = testCredId,
          groupIdOpt = Some(testGroupId),
          agentCodeOpt = None,
          expectedRetrievals = Seq(
            "allEnrolments",
            "groupIdentifier",
            "optionalCredentials"
          )
        )
        populateMappings(enrolmentType -> Seq(AgentReferenceMapping(
          created = Instant.now(),
          arn,
          identifierValue
        )))
        givenAgentRecordWithChecks(
          arn,
          Json.obj(
            "suspensionDetails" -> Json.obj(
              "suspensionStatus" -> false
            )
          )
        )

        val response = callGet(s"/mappings-status/enrolment/${enrolmentType.dbKey}/value/$identifierValue")

        response.status shouldBe 204

      "return locked when the ARN is suspended" in:
        givenUserIsAuthorisedFor(
          enrolmentType.key,
          enrolmentType.toString,
          identifierValue,
          ggCredId = testCredId,
          groupIdOpt = Some(testGroupId),
          agentCodeOpt = None,
          expectedRetrievals = Seq(
            "allEnrolments",
            "groupIdentifier",
            "optionalCredentials"
          )
        )
        populateMappings(enrolmentType -> Seq(AgentReferenceMapping(
          created = Instant.now(),
          arn,
          identifierValue
        )))
        givenAgentRecordWithChecks(
          arn,
          Json.obj(
            "suspensionDetails" -> Json.obj(
              "suspensionStatus" -> true
            )
          )
        )

        val response = callGet(s"/mappings-status/enrolment/${enrolmentType.dbKey}/value/$identifierValue")

        response.status shouldBe 423
        response.json shouldBe Json.obj(
          "continueUrl" -> "suspension page url"
        )

    "the identifier is not mapped" should:
      "return not found after the mandation date if the agent's group has no other mappings" in:
        testClock.setDate(afterMandationDate)
        givenUserIsAuthorisedFor(
          enrolmentType.key,
          enrolmentType.toString,
          identifierValue,
          ggCredId = testCredId,
          groupIdOpt = Some(testGroupId),
          agentCodeOpt = None,
          expectedRetrievals = Seq(
            "allEnrolments",
            "groupIdentifier",
            "optionalCredentials"
          )
        )
        givenPrincipalEnrolmentsExist(
          testGroupId,
          Seq(
            Enrolment(
              service = enrolmentType.key,
              state = "Activated",
              identifiers = Seq(
                EnrolmentIdentifier(
                  key = enrolmentType.toString,
                  value = identifierValue
                )
              )
            )
          )
        )

        val response = callGet(s"/mappings-status/enrolment/${enrolmentType.dbKey}/value/$identifierValue")

        response.status shouldBe 404
        response.json shouldBe Json.obj(
          "continueUrl" -> "mapping journey url"
        )

      "return no content and automap when another identifier for the agent is mapped to an ARN" in:
        testClock.setDate(afterMandationDate)
        givenUserIsAuthorisedFor(
          enrolmentType.key,
          enrolmentType.toString,
          identifierValue,
          ggCredId = testCredId,
          groupIdOpt = Some(testGroupId),
          agentCodeOpt = None,
          expectedRetrievals = Seq(
            "allEnrolments",
            "groupIdentifier",
            "optionalCredentials"
          )
        )
        givenPrincipalEnrolmentsExist(
          testGroupId,
          Seq(
            Enrolment(
              service = enrolmentType.key,
              state = "Activated",
              identifiers = Seq(
                EnrolmentIdentifier(
                  key = enrolmentType.toString,
                  value = identifierValue
                )
              )
            ),
            Enrolment(
              service = otherEnrolmentType.key,
              state = "Activated",
              identifiers = Seq(
                EnrolmentIdentifier(
                  key = otherEnrolmentType.toString,
                  value = otherIdentifierValue
                )
              )
            ),
            Enrolment(
              service = otherEnrolmentType2.key,
              state = "Activated",
              identifiers = Seq(
                EnrolmentIdentifier(
                  key = otherEnrolmentType2.toString,
                  value = otherIdentifierValue2
                )
              )
            )
          )
        )

        populateMappings(
          otherEnrolmentType -> Seq(AgentReferenceMapping(
            created = Instant.now(),
            arn,
            otherIdentifierValue
          )),
          otherEnrolmentType2 -> Seq(AgentReferenceMapping(
            created = Instant.now(),
            arn,
            otherIdentifierValue2
          ))
        )
        givenAgentRecordWithChecks(
          arn,
          Json.obj(
            "suspensionDetails" -> Json.obj(
              "suspensionStatus" -> false
            )
          )
        )

        mappingRepositories.get(enrolmentType).findArnBy(identifierValue).futureValue shouldBe Nil

        val response = callGet(s"/mappings-status/enrolment/${enrolmentType.dbKey}/value/$identifierValue")

        response.status shouldBe 204

        mappingRepositories.get(enrolmentType).findArnBy(identifierValue).futureValue shouldBe Seq(arn)

      "return not found when another identifier for the agent is mapped to multiple ARNs" in:
        testClock.setDate(afterMandationDate)
        givenUserIsAuthorisedFor(
          enrolmentType.key,
          enrolmentType.toString,
          identifierValue,
          ggCredId = testCredId,
          groupIdOpt = Some(testGroupId),
          agentCodeOpt = None,
          expectedRetrievals = Seq(
            "allEnrolments",
            "groupIdentifier",
            "optionalCredentials"
          )
        )
        givenPrincipalEnrolmentsExist(
          testGroupId,
          Seq(
            Enrolment(
              service = enrolmentType.key,
              state = "Activated",
              identifiers = Seq(
                EnrolmentIdentifier(
                  key = enrolmentType.toString,
                  value = identifierValue
                )
              )
            ),
            Enrolment(
              service = otherEnrolmentType.key,
              state = "Activated",
              identifiers = Seq(
                EnrolmentIdentifier(
                  key = otherEnrolmentType.toString,
                  value = otherIdentifierValue
                )
              )
            ),
            Enrolment(
              service = otherEnrolmentType2.key,
              state = "Activated",
              identifiers = Seq(
                EnrolmentIdentifier(
                  key = otherEnrolmentType2.toString,
                  value = otherIdentifierValue2
                )
              )
            )
          )
        )

        populateMappings(
          otherEnrolmentType -> Seq(AgentReferenceMapping(
            created = Instant.now(),
            arn,
            otherIdentifierValue
          )),
          otherEnrolmentType2 -> Seq(AgentReferenceMapping(
            created = Instant.now(),
            arn2,
            otherIdentifierValue2
          ))
        )

        val response = callGet(s"/mappings-status/enrolment/${enrolmentType.dbKey}/value/$identifierValue")

        response.status shouldBe 404
        response.json shouldBe Json.obj(
          "continueUrl" -> "mapping journey url"
        )
        mappingRepositories.get(enrolmentType).findArnBy(identifierValue).futureValue shouldBe Nil

    "the identifier is not mapped before the mandation date" should:
      "return ok with alreadyNotified as false on the first call and true on the second call" in:
        testClock.setDate(beforeMandationDate)
        givenUserIsAuthorisedFor(
          enrolmentType.key,
          enrolmentType.toString,
          identifierValue,
          ggCredId = testCredId,
          groupIdOpt = Some(testGroupId),
          agentCodeOpt = None,
          expectedRetrievals = Seq(
            "allEnrolments",
            "groupIdentifier",
            "optionalCredentials"
          )
        )
        givenPrincipalEnrolmentsExist(
          testGroupId,
          Seq(
            Enrolment(
              service = enrolmentType.key,
              state = "Activated",
              identifiers = Seq(
                EnrolmentIdentifier(
                  key = enrolmentType.toString,
                  value = identifierValue
                )
              )
            )
          )
        )

        val testSessionId = "testSessionId"

        lazy val firstResponse = callGet(s"/mappings-status/enrolment/${enrolmentType.dbKey}/value/$identifierValue", Some(testSessionId))
        firstResponse.status shouldBe 200
        firstResponse.json shouldBe Json.obj(
          "continueUrl" -> "mapping journey/notification url",
          "alreadyNotified" -> false
        )

        lazy val secondResponse = callGet(s"/mappings-status/enrolment/${enrolmentType.dbKey}/value/$identifierValue", Some(testSessionId))
        secondResponse.status shouldBe 200
        secondResponse.json shouldBe Json.obj(
          "continueUrl" -> "mapping journey/notification url",
          "alreadyNotified" -> true
        )

end MappingStatusControllerISpec
