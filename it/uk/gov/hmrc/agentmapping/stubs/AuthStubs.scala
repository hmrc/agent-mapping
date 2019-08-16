package uk.gov.hmrc.agentmapping.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolment}

trait AuthStubs {

  def givenUserNotAuthorisedWithError(mdtpDetail: String): StubMapping = {
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", s"""MDTP detail="$mdtpDetail"""")))
  }


  def givenUserIsAuthorisedFor(
                                enrolmentKey: String,
                                identifierName: String,
                                identifierValue: String,
                                ggCredId: String,
                                affinityGroup: AffinityGroup = AffinityGroup.Agent,
                                agentCodeOpt: Option[String],
                                expectedRetrievals: Seq[String] = Seq("optionalCredentials", "agentCode", "allEnrolments")): StubMapping = {
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
                 |{ "optionalCredentials": {
                 |    "providerId": "$ggCredId",
                 |    "providerType": "GovernmmentGateway"
                 |  }
                 |  ${agentCodeOpt.map(ac => s""", "agentCode": "$ac" """).getOrElse("")},
                 |  "allEnrolments": [
                 |    { "key":"$enrolmentKey", "identifiers": [
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

  def givenUserIsAuthorisedForCreds(
                                serviceName: String,
                                identifierName: String,
                                identifierValue: String,
                                ggCredId: String,
                                affinityGroup: AffinityGroup = AffinityGroup.Agent,
                                agentCodeOpt: Option[String],
                                expectedRetrievals: Seq[String] = Seq("optionalCredentials")): StubMapping = {
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
                 |{ "optionalCredentials": {
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
                                        agentCodeOpt: Option[String]): StubMapping = {
    val responseBody =
      s"""
         |{ "optionalCredentials": {
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
              |  "retrieve": ["optionalCredentials","agentCode","allEnrolments"]
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
          .withHeader("WWW-Authenticate", "MDTP detail=\"InsufficientEnrolments\""))
    )
  }

  def givenUserIsAuthorisedAsAgent(arn: String): StubMapping = {
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

  def givenUserIsAuthorisedWithNoEnrolments(
                                             enrolmentKey: String,
                                             identifierName: String,
                                             identifierValue: String,
                                             ggCredId: String,
                                             affinityGroup: AffinityGroup = AffinityGroup.Agent,
                                             agentCodeOpt: Option[String]): StubMapping = {
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .atPriority(1)
        .withRequestBody(equalToJson(
          s"""{"authorise":[
             |  {"authProviders":["GovernmentGateway"]},
             |  {"affinityGroup":"$affinityGroup"}
             |],
             |"retrieve":["allEnrolments"]
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
                 |{ "optionalCredentials": {
                 |    "providerId": "$ggCredId",
                 |    "providerType": "GovernmmentGateway"
                 |  }
                 |  ${agentCodeOpt.map(ac => s""", "agentCode": "$ac" """).getOrElse("")},
                 |  "allEnrolments": []
                 |}
                 |""".stripMargin)))
  }

  def isLoggedIn = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(200).withBody("{}")))
    this
  }
}
