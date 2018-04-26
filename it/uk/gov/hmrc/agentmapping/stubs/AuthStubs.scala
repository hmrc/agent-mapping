package uk.gov.hmrc.agentmapping.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.auth.core.{ AffinityGroup, Enrolment }

trait AuthStubs {

  def givenUserNotAuthorisedWithError(mdtpDetail: String): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(401)
        .withHeader("WWW-Authenticate", s"""MDTP detail="$mdtpDetail"""")))
  }

  def givenUserAuthorisedFor(serviceName: String, identifierName: String, identifierValue: String, ggCredId: String, affinityGroup: AffinityGroup = AffinityGroup.Agent, agentCodeOpt: Option[String]): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise")).atPriority(1)
      .withRequestBody(
        equalToJson(
          s"""{"authorise":[
             |  {"authProviders":["GovernmentGateway"]},
             |  {"affinityGroup":"$affinityGroup"}
             |],
             |"retrieve":["credentials","agentCode","allEnrolments"]
          }""".stripMargin, true, false))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(
          s"""
             |{ "credentials": {
             |    "providerId": "$ggCredId",
             |    "providerType": "GovernmmentGateway"
             |  }
             |  ${agentCodeOpt.map(ac => s""", "agentCode": "$ac" """).getOrElse("")},
             |  "allEnrolments": [
             |    { "key":"$serviceName", "identifiers": [
             |      {"key":"$identifierName", "value": "$identifierValue"}
             |    ], "state": "Activated" }
             |  ]
             |}
             |""".stripMargin)))

    stubFor(post(urlEqualTo("/auth/authorise")).atPriority(2)
      .willReturn(aResponse()
        .withStatus(401)
        .withHeader("WWW-Authenticate", "MDTP detail=\"InsufficientEnrolments\"")))
  }

  def givenUserAuthorisedForMultiple(enrolments: Set[Enrolment], ggCredId: String, affinityGroup: AffinityGroup = AffinityGroup.Agent, agentCodeOpt: Option[String]): Unit = {
    val responseBody = s"""
                          |{ "credentials": {
                          |    "providerId": "$ggCredId",
                          |    "providerType": "GovernmmentGateway"
                          |  }
                          |  ${agentCodeOpt.map(ac => s""", "agentCode": "$ac" """).getOrElse("")},
                          |  "allEnrolments": [
                          |    ${
      enrolments.map(e => s"""
                                                        |{ "key":"${e.key}",
                                                        |"identifiers": [${
        e.identifiers.map(i =>
          s"""{
         |"key":"${i.key}",
         |"value": "${i.value}"
         |}""".stripMargin).mkString(",")
      }],
                                                        |"state": "Activated" } """.stripMargin).mkString(",")
    }
                          |                   ]
                          |}""".stripMargin
    stubFor(post(urlEqualTo("/auth/authorise")).atPriority(1)
      .withRequestBody(
        equalToJson(
          s"""|
              |{ "authorise": [
              |     {"authProviders":["GovernmentGateway"]},
              |     {"affinityGroup":"$affinityGroup"}
              |   ],
              |  "retrieve": ["credentials","agentCode","allEnrolments"]
              |}
           """.stripMargin, true, false))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(responseBody)))

    stubFor(post(urlEqualTo("/auth/authorise")).atPriority(2)
      .willReturn(aResponse()
        .withStatus(401)
        .withHeader("WWW-Authenticate", "MDTP detail=\"InsufficientEnrolments\"")))
  }

}