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

package uk.gov.hmrc.agentmapping.stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.Json
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.connector.EnrolmentResponse
import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector.writes

trait EnrolmentStoreStubs:

  val appConfig: AppConfig

  def givenEs2ClientsFoundFor(
    userId: String,
    service: String,
    startRecord: Int,
    enrolments: EnrolmentResponse,
    status: Int
  ): StubMapping =
    stubFor(
      get(
        urlEqualTo(
          s"/enrolment-store-proxy/enrolment-store/users/$userId/enrolments?type=delegated&service=$service&start-record=$startRecord&max-records=${appConfig.clientCountBatchSize}"
        )
      )
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(
              s"${Json.stringify(Json.toJson(enrolments))}"
            )
        )
    )
  end givenEs2ClientsFoundFor

  def givenPrincipalEnrolmentsExist(
    groupId: String,
    enrolments: Seq[uk.gov.hmrc.agentmapping.model.Enrolment],
    status: Int = 200
  ): StubMapping =

    stubFor(
      get(urlPathEqualTo(s"/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments"))
        .withQueryParam("type", equalTo("principal"))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(
              if (status == 204)
                ""
              else
                Json.stringify(Json.obj(
                  "enrolments" -> enrolments
                ))
            )
        )
    )
  end givenPrincipalEnrolmentsExist

end EnrolmentStoreStubs
