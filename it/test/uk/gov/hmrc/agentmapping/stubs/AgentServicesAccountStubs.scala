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

package uk.gov.hmrc.agentmapping.stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.agentmapping.model.Arn

trait AgentServicesAccountStubs:

  def givenAgentRecordWithChecks(
    arn: Arn,
    responseBody: JsObject,
    status: Int = 200
  ): StubMapping =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/agent-record-with-checks/arn/${arn.value}"))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.stringify(responseBody))
        )
    )

end AgentServicesAccountStubs
