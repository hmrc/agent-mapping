package uk.gov.hmrc.agentmapping.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolment}
import uk.gov.hmrc.http.SessionKeys

trait AuthStubs {

  def givenUserNotAuthorisedWithError(mdtpDetail: String): Unit =
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", s"""MDTP detail="$mdtpDetail"""")))

  def givenUserIsAuthorisedFor(
                                serviceName: String,
                                identifierName: String,
                                identifierValue: String,
                                ggCredId: String,
                                affinityGroup: AffinityGroup = AffinityGroup.Agent,
                                agentCodeOpt: Option[String],
                                expectedRetrievals: Seq[String] = Seq("credentials", "agentCode", "allEnrolments")): Unit = {
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .atPriority(1)
        .withRequestBody(equalToJson(
          s"""{"authorise":[
             |  {"authProviders":["GovernmentGateway"]},
             |  {"affinityGroup":"$affinityGroup"}
             |],
             |"retrieve":[${expectedRetrievals.mkString("\"", "\",\"", "\"")}]
          }""".stripMargin,
          true,
          false
        ))
        .willReturn(
          aResponse()
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

    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .atPriority(2)
        .willReturn(aResponse()
          .withStatus(401)
          .withHeader("WWW-Authenticate", "MDTP detail=\"InsufficientEnrolments\"")))
  }

  def givenUserIsAuthorisedForMultiple(
                                        enrolments: Set[Enrolment],
                                        ggCredId: String,
                                        affinityGroup: AffinityGroup = AffinityGroup.Agent,
                                        agentCodeOpt: Option[String]): Unit = {
    val responseBody =
      s"""
         |{ "credentials": {
         |    "providerId": "$ggCredId",
         |    "providerType": "GovernmmentGateway"
         |  }
         |  ${agentCodeOpt.map(ac => s""", "agentCode": "$ac" """).getOrElse("")},
         |  "allEnrolments": [
         |    ${
        enrolments.map(e =>
          s"""
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
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .atPriority(1)
        .withRequestBody(equalToJson(
          s"""|
              |{ "authorise": [
              |     {"authProviders":["GovernmentGateway"]},
              |     {"affinityGroup":"$affinityGroup"}
              |   ],
              |  "retrieve": ["credentials","agentCode","allEnrolments"]
              |}
           """.stripMargin,
          true,
          false
        ))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)))

    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .atPriority(2)
        .willReturn(aResponse()
          .withStatus(401)
          .withHeader("WWW-Authenticate", "MDTP detail=\"InsufficientEnrolments\"")))
  }

  def givenUserIsAuthorisedAsAgent(arn: String) = {
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .withRequestBody(equalToJson(
          s"""
             |{
             |  "authorise": [
             |    { "identifiers":[], "state":"Activated", "enrolment": "HMRC-AS-AGENT" },
             |    { "authProviders": ["GovernmentGateway"] }
             |  ],
             |  "retrieve":["authorisedEnrolments"]
             |}
           """.stripMargin, true, true))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              s"""
                 |{
                 |"authorisedEnrolments": [
                 |  { "key":"HMRC-AS-AGENT", "identifiers": [
                 |    {"key":"AgentReferenceNumber", "value": "$arn"}
                 |  ]}
                 |]}
          """.stripMargin)))

  }
}
