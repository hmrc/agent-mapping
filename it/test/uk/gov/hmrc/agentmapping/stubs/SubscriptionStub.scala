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

package test.uk.gov.hmrc.agentmapping.stubs

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.agentmapping.model.AuthProviderId
import uk.gov.hmrc.agentmapping.model.UserMapping
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

trait SubscriptionStub {

  def givenUserMappingsNotFoundForAuthProviderId(authProviderId: AuthProviderId): StubMapping = stubFor(
    get(urlPathEqualTo(s"/agent-subscription/subscription/journey/id/${encodePathSegment(authProviderId.id)}"))
      .willReturn(
        aResponse()
          .withStatus(Status.NO_CONTENT)
      )
  )

  def givenUserMappingsExistsForAuthProviderId(
    authProviderId: AuthProviderId,
    userMappings: Seq[UserMapping]
  ): StubMapping = stubFor(
    get(urlPathEqualTo(s"/agent-subscription/subscription/journey/id/${encodePathSegment(authProviderId.id)}"))
      .willReturn(
        aResponse()
          .withStatus(Status.OK)
          .withBody(s"""{"userMappings": ${Json.toJson(userMappings).toString()}}""")
      )
  )

  def givenNoMappingsExistForAuthProviderId(authProviderId: AuthProviderId): StubMapping = stubFor(
    get(urlPathEqualTo(s"/agent-subscription/subscription/journey/id/${encodePathSegment(authProviderId.id)}"))
      .willReturn(
        aResponse()
          .withStatus(Status.OK)
          .withBody(s"""{"userMappings": []}""")
      )
  )

}
