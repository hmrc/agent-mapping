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

package uk.gov.hmrc.agentmapping.connector

import org.scalatest.RecoverMethods.recoverToExceptionIf
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.stubs.AgentServicesAccountStubs
import uk.gov.hmrc.agentmapping.support.BaseISpec
import uk.gov.hmrc.agentmapping.support.WireMockSupport
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.ExecutionContext.Implicits.global

class AgentServicesAccountConnectorISpec
extends BaseISpec
with WireMockSupport
with AgentServicesAccountStubs
with GuiceOneAppPerSuite:

  override lazy val app: Application = appBuilder.build()

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  private lazy val connector: AgentServicesAccountConnector = app.injector.instanceOf[AgentServicesAccountConnector]

  private val arn = Arn("TARN0000001")

  "isSuspended" should {

    "return true when suspensionStatus is true" in {
      givenAgentRecordWithChecks(
        arn,
        Json.obj(
          "suspensionDetails" -> Json.obj(
            "suspensionStatus" -> true
          )
        )
      )

      connector.isSuspended(arn).futureValue shouldBe true
    }

    "return false when suspensionStatus is false" in {
      givenAgentRecordWithChecks(
        arn,
        Json.obj(
          "suspensionDetails" -> Json.obj(
            "suspensionStatus" -> false
          )
        )
      )

      connector.isSuspended(arn).futureValue shouldBe false
    }

    "return false when suspensionDetails are missing" in {
      givenAgentRecordWithChecks(
        arn,
        Json.obj(
          "arn" -> arn.value
        )
      )

      connector.isSuspended(arn).futureValue shouldBe false
    }

    "throw UpstreamErrorResponse when the service responds with a non-200 status" in {
      givenAgentRecordWithChecks(
        arn,
        Json.obj(
          "message" -> "bad gateway"
        ),
        status = 502
      )

      val exception =
        recoverToExceptionIf[UpstreamErrorResponse] {
          connector.isSuspended(arn)
        }.futureValue

      exception.statusCode shouldBe 502
    }

  }

end AgentServicesAccountConnectorISpec
