package uk.gov.hmrc.agentmapping.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.auth.core.authorise.AffinityGroup

trait AuthStubs {

  def givenUserNotAuthorisedWithError(mdtpDetail: String): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(401)
        .withHeader("WWW-Authenticate", s"""MDTP detail="${mdtpDetail}"""")
      )
    )
  }

  def givenUserAuthorisedFor(enrolment: String, identifierName: String, identifierValue: String, ggCredId: String, affinityGroup: AffinityGroup = AffinityGroup.Agent): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise")).atPriority(1)
      .withRequestBody(
        equalToJson(
          s"""{"authorise":[
             |{"authProviders":["GovernmentGateway"]},
             |{"affinityGroup":"$affinityGroup"},
             |{"identifiers":[{"key":"$identifierName","value":"$identifierValue"}],
             |"state":"Activated",
             |"enrolment":"$enrolment"}]
          }""".stripMargin, true, true))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(s"""{"authProviderId":{"ggCredId":"$ggCredId"}}""")))

    stubFor(post(urlEqualTo("/auth/authorise")).atPriority(2)
      .willReturn(aResponse()
        .withStatus(401)
        .withHeader("WWW-Authenticate", "MDTP detail=\"InsufficientEnrolments\"")
      )
    )
  }
}