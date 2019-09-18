package uk.gov.hmrc.agentmapping.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlPathEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.agentmapping.model.{AuthProviderId, UserMapping}
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

trait SubscriptionStub {

  def givenUserMappingsNotFoundForAuthProviderId(authProviderId: AuthProviderId): StubMapping =
    stubFor(
      get(urlPathEqualTo(s"/agent-subscription/subscription/journey/id/${encodePathSegment(authProviderId.id)}"))
        .willReturn(aResponse()
          .withStatus(Status.NO_CONTENT)))

  def givenUserMappingsExistsForAuthProviderId(authProviderId: AuthProviderId, userMappings: Seq[UserMapping]): StubMapping =
    stubFor(
      get(urlPathEqualTo(s"/agent-subscription/subscription/journey/id/${encodePathSegment(authProviderId.id)}"))
        .willReturn(aResponse()
          .withStatus(Status.OK)
          .withBody(s"""{"userMappings": ${Json.toJson(userMappings).toString()}}"""))
    )

  def givenNoMappingsExistForAuthProviderId(authProviderId: AuthProviderId): StubMapping =
    stubFor(
      get(urlPathEqualTo(s"/agent-subscription/subscription/journey/id/${encodePathSegment(authProviderId.id)}"))
        .willReturn(aResponse()
          .withStatus(Status.OK)
          .withBody(s"""{"userMappings": []}"""))
    )

}
