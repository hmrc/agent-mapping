package uk.gov.hmrc.agentmapping.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.auth.core.{ AffinityGroup, Enrolment }

trait AuthStubs {

  def givenUserNotAuthorisedWithError(mdtpDetail: String): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(401)
        .withHeader("WWW-Authenticate", s"""MDTP detail="${mdtpDetail}"""")))
  }

  def givenUserAuthorisedFor(enrolment: String, identifierName: String, identifierValue: String, ggCredId: String, affinityGroup: AffinityGroup = AffinityGroup.Agent, agentCodeOpt: Option[String]): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise")).atPriority(1)
      .withRequestBody(
        equalToJson(
          s"""{"authorise":[
             |  {"authProviders":["GovernmentGateway"]},
             |  {"affinityGroup":"$affinityGroup"},
             |  {
             |    "identifiers":[
             |      {"key":"$identifierName","value":"$identifierValue"}
             |    ],
             |    "state":"Activated",
             |    "enrolment":"$enrolment"
             |  }
             |],
             |"retrieve":["credentials","agentCode"]
          }""".stripMargin, true, false))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(s"""{ "credentials": {"providerId": "$ggCredId", "providerType": "GovernmmentGateway"} ${agentCodeOpt.map(ac => s""", "agentCode": "$ac" """).getOrElse("")} }""")))

    stubFor(post(urlEqualTo("/auth/authorise")).atPriority(2)
      .willReturn(aResponse()
        .withStatus(401)
        .withHeader("WWW-Authenticate", "MDTP detail=\"InsufficientEnrolments\"")))
  }

  def givenUserAuthorisedForMultiple(enrolments: Set[Enrolment], ggCredId: String, affinityGroup: AffinityGroup = AffinityGroup.Agent, agentCodeOpt: Option[String]): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise")).atPriority(1)
      .withRequestBody(
        equalToJson(
          s"""|
              |{ "authorise":[
              |   {"authProviders":["GovernmentGateway"]},
              |   {"affinityGroup":"$affinityGroup"},
              |   ${
            enrolments.map(e => s"""{
              |      "identifiers":[${e.identifiers.map(i => s"""{"key":"${i.key}","value":"${i.value}"}""").mkString(",")}],
              |      "state":"${e.state}",
              |      "enrolment":"${e.key}"
              |      }""".stripMargin).mkString(",")
          }
              |   ],
              |  "retrieve":["credentials","agentCode"]
              |}
           """.stripMargin, true, false))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(s"""{ "credentials": {"providerId": "$ggCredId", "providerType": "GovernmmentGateway"} ${agentCodeOpt.map(ac => s""", "agentCode": "$ac" """).getOrElse("")} }""")))

    stubFor(post(urlEqualTo("/auth/authorise")).atPriority(2)
      .willReturn(aResponse()
        .withStatus(401)
        .withHeader("WWW-Authenticate", "MDTP detail=\"InsufficientEnrolments\"")))
  }

}