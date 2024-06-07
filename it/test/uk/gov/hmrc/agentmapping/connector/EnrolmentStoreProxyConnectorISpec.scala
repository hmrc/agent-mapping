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

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import uk.gov.hmrc.agentmapping.config.AppConfig
import test.uk.gov.hmrc.agentmapping.stubs.EnrolmentStoreStubs
import test.uk.gov.hmrc.agentmapping.support.{BaseISpec, WireMockSupport}
import uk.gov.hmrc.agentmapping.connector.{Enrolment, EnrolmentResponse, EnrolmentStoreProxyConnector}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global

class EnrolmentStoreProxyConnectorISpec
    extends BaseISpec
    with WireMockSupport
    with GuiceOneAppPerSuite
    with EnrolmentStoreStubs {

  implicit val hc = HeaderCarrier()

  private lazy implicit val metrics = app.injector.instanceOf[Metrics]
  private lazy val http = app.injector.instanceOf[HttpClient]

  override implicit lazy val app: Application = appBuilder.build()

  val appConfig = app.injector.instanceOf[AppConfig]

  private lazy val connector: EnrolmentStoreProxyConnector =
    new EnrolmentStoreProxyConnector(appConfig, http, metrics)

  private val maxRecordsToDisplay = appConfig.clientCountMaxResults
  private val clientCountBatchSize = appConfig.clientCountBatchSize

  private val IR_SA = "IR-SA"

  private def batchResponse(recordsToReturn: Int, allActive: Boolean = true) =
    if (allActive) EnrolmentResponse(List.fill(recordsToReturn)(Enrolment("Activated")))
    else {
      EnrolmentResponse(Enrolment("Not Activated") :: List.fill(recordsToReturn - 1)(Enrolment("Activated")))
    }

  "runEs2ForServices" should {
    s"return $maxRecordsToDisplay if the total records from $IR_SA is higher than $maxRecordsToDisplay" in {
      givenEs2ClientsFoundFor("agent1", IR_SA, 1, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 8, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 15, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 22, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 29, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 36, batchResponse(clientCountBatchSize), 200)

      connector.getClientCount("agent1").futureValue shouldBe 40
    }

    s"return the actual number of records when the total from $IR_SA is less than $maxRecordsToDisplay" in {
      givenEs2ClientsFoundFor("agent1", IR_SA, 1, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 8, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 15, batchResponse(clientCountBatchSize - 1), 200)

      connector.getClientCount("agent1").futureValue shouldBe 20
    }

    s"return the count of only Activated records" in {
      givenEs2ClientsFoundFor("agent1", IR_SA, 1, batchResponse(clientCountBatchSize, false), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 8, batchResponse(clientCountBatchSize - 2), 200)

      connector.getClientCount("agent1").futureValue shouldBe 11
    }

    "return 0 if the call to ESP returns 204" in {
      givenEs2ClientsFoundFor("agent1", IR_SA, 1, batchResponse(0), 204)

      connector.getClientCount("agent1").futureValue shouldBe 0
    }

    "throw an exception if the call to ESP does not work" in {
      givenEs2ClientsFoundFor("agent1", IR_SA, 1, batchResponse(0), 502)

      val exception = intercept[RuntimeException] {
        connector.getClientCount("agent1").futureValue
      }
      exception.getMessage.contains("Error retrieving client list from") shouldBe true
    }

  }
}
