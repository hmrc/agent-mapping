package uk.gov.hmrc.agentmapping.stubs

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}

trait DesStubs {
  protected def expectedEnvironment: Option[String] = None
  protected def expectedBearerToken: Option[String] = None

  val registeredArn : Arn = Arn("AARN0000002")

  def individualRegistrationExists(utr: Utr, isAnASAgent: Boolean = true): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""{ "agentReferenceNumber": "${registeredArn.value}" }""".stripMargin)))
  }

  def individualRegistrationExistsWithoutArn(utr: Utr, isAnASAgent: Boolean = true): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""{}""".stripMargin)))
  }

  def registrationDoesNotExist(utr: Utr): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(404)
        .withBody(notFoundResponse)))
  }

  private def registrationRequest(utr: Utr, isAnAgent: Boolean) =
    post(urlEqualTo(s"/registration/individual/utr/${utr.value}"))
      .withRequestBody(equalToJson(
        s"""
           |{
           |  "requiresNameMatch": false,
           |  "regime": "ITSA",
           |  "isAnAgent": $isAnAgent
           |}
              """.stripMargin))

  private def maybeWithDesHeaderCheck(mappingBuilder: MappingBuilder): MappingBuilder =
    maybeWithOptionalAuthorizationHeaderCheck(maybeWithEnvironmentHeaderCheck(mappingBuilder))

  private def maybeWithOptionalAuthorizationHeaderCheck(mappingBuilder: MappingBuilder): MappingBuilder =
    expectedBearerToken match {
      case Some(token) => mappingBuilder.withHeader("Authorization", equalTo(s"Bearer $token"))
      case None => mappingBuilder
    }

  private def maybeWithEnvironmentHeaderCheck(mappingBuilder: MappingBuilder): MappingBuilder =
    expectedEnvironment match {
      case Some(environment) => mappingBuilder.withHeader("Environment", equalTo(environment))
      case None => mappingBuilder
    }

  private val notFoundResponse = errorResponse("NOT_FOUND", "The remote endpoint has indicated that no data can be found.")

  private def errorResponse(code: String, reason: String) =
    s"""
       |{
       |  "code": "$code",
       |  "reason": "$reason"
       |}
     """.stripMargin
}
