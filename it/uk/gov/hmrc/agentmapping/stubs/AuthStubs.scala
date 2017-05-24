package uk.gov.hmrc.agentmapping.stubs

import com.github.tomakehurst.wiremock.client.WireMock._

trait AuthStubs {

  def givenAuthority(credId: String): Unit ={
    stubFor(get(urlPathEqualTo(s"/auth/authority")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |{
         |    "credId": "$credId"
         |}
       """.stripMargin
    )))
  }
}