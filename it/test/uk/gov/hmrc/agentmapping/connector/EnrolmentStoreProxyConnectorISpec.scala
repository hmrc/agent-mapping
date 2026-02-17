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

package test.uk.gov.hmrc.agentmapping.connector

import org.scalatest.RecoverMethods.recoverToExceptionIf
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import test.uk.gov.hmrc.agentmapping.stubs.EnrolmentStoreStubs
import test.uk.gov.hmrc.agentmapping.support.BaseISpec
import test.uk.gov.hmrc.agentmapping.support.WireMockSupport
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.model.AgentCode
import uk.gov.hmrc.agentmapping.model.EnrolmentIdentifier
import uk.gov.hmrc.agentmapping.model.{Enrolment => ModelEnrolment}
import uk.gov.hmrc.agentmapping.connector.EnrolmentResponse
import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global

class EnrolmentStoreProxyConnectorISpec
extends BaseISpec
with WireMockSupport
with GuiceOneAppPerSuite
with EnrolmentStoreStubs {

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  private implicit lazy val metrics: Metrics = app.injector.instanceOf[Metrics]
  private lazy val http = app.injector.instanceOf[HttpClientV2]

  override implicit lazy val app: Application = appBuilder.build()

  val appConfig = app.injector.instanceOf[AppConfig]

  private lazy val connector: EnrolmentStoreProxyConnector =
    new EnrolmentStoreProxyConnector(
      appConfig,
      http,
      metrics
    )

  private val maxRecordsToDisplay = appConfig.clientCountMaxResults
  private val clientCountBatchSize = appConfig.clientCountBatchSize

  private val IR_SA = "IR-SA"

  private def batchResponse(
    recordsToReturn: Int,
    allActive: Boolean = true
  ) =
    if (allActive)
      EnrolmentResponse(List.fill(recordsToReturn)(
        ModelEnrolment(
          IR_SA,
          "Activated",
          Seq(EnrolmentIdentifier("UTR", "1234567890"))
        )
      ))
    else {
      EnrolmentResponse(ModelEnrolment(
        IR_SA,
        "Not Activated",
        Seq(EnrolmentIdentifier("UTR", "1234567890"))
      ) :: List.fill(recordsToReturn - 1)(ModelEnrolment(
        AgentCode.key,
        "Activated",
        Seq(EnrolmentIdentifier("UTR", "1234567890"))
      )))
    }

  "runEs2ForServices" should {
    s"return $maxRecordsToDisplay if the total records from $IR_SA is higher than $maxRecordsToDisplay" in {
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        1,
        batchResponse(clientCountBatchSize),
        200
      )
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        8,
        batchResponse(clientCountBatchSize),
        200
      )
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        15,
        batchResponse(clientCountBatchSize),
        200
      )
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        22,
        batchResponse(clientCountBatchSize),
        200
      )
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        29,
        batchResponse(clientCountBatchSize),
        200
      )
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        36,
        batchResponse(clientCountBatchSize),
        200
      )

      connector.getClientCount("agent1").futureValue shouldBe 40
    }

    s"return the actual number of records when the total from $IR_SA is less than $maxRecordsToDisplay" in {
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        1,
        batchResponse(clientCountBatchSize),
        200
      )
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        8,
        batchResponse(clientCountBatchSize),
        200
      )
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        15,
        batchResponse(clientCountBatchSize - 1),
        200
      )

      connector.getClientCount("agent1").futureValue shouldBe 20
    }

    s"return the count of only Activated records" in {
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        1,
        batchResponse(clientCountBatchSize, false),
        200
      )
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        8,
        batchResponse(clientCountBatchSize - 2),
        200
      )

      connector.getClientCount("agent1").futureValue shouldBe 11
    }

    "return 0 if the call to ESP returns 204" in {
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        1,
        batchResponse(0),
        204
      )

      connector.getClientCount("agent1").futureValue shouldBe 0
    }

    "throw an exception if the call to ESP does not work" in {
      givenEs2ClientsFoundFor(
        "agent1",
        IR_SA,
        1,
        batchResponse(0),
        502
      )

      val exception = intercept[RuntimeException] {
        connector.getClientCount("agent1").futureValue
      }
      exception.getMessage.contains("Error retrieving client list from") shouldBe true
    }

  }
  "getPrincipalEnrolments" should {

    "return enrolments when ESP responds with 200 OK" in {

      val groupId = "group-123"

      val enrolments = Seq(
        uk.gov.hmrc.agentmapping.model.Enrolment(
          service = "IR-SA",
          state = "Activated",
          identifiers = Seq.empty
        ),
        uk.gov.hmrc.agentmapping.model.Enrolment(
          service = "IR-CT",
          state = "Activated",
          identifiers = Seq.empty
        )
      )

      givenPrincipalEnrolmentsExist(groupId, enrolments)

      val result = connector.getPrincipalEnrolments(groupId).futureValue

      result.enrolments shouldBe enrolments
    }

    "return empty sequence when ESP responds with 204 NO_CONTENT" in {

      val groupId = "group-123"

      givenPrincipalEnrolmentsExist(
        groupId = groupId,
        enrolments = Seq.empty,
        status = 204
      )

      val result = connector.getPrincipalEnrolments(groupId).futureValue

      result.enrolments shouldBe Seq.empty
    }

    "throw UpstreamErrorResponse when ESP responds with non-200/204 status" in {

      val groupId = "group-123"

      givenPrincipalEnrolmentsExist(
        groupId = groupId,
        enrolments = Seq.empty,
        status = 502
      )

      val exception =
        recoverToExceptionIf[uk.gov.hmrc.http.UpstreamErrorResponse] {
          connector.getPrincipalEnrolments(groupId)
        }.futureValue

      exception.statusCode shouldBe 502
    }

  }

}
