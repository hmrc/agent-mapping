package uk.gov.hmrc.agentmapping.stubs


import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.connector.EnrolmentResponse

trait EnrolmentStoreStubs {

  val appConfig: AppConfig

  def givenEs2ClientsFoundFor(userId: String,
                              service: String,
                              startRecord: Int,
                              enrolments: EnrolmentResponse, status: Int) = {

    import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector.writes
    stubFor(
      get(urlEqualTo(s"/enrolment-store-proxy/enrolment-store/users/$userId/enrolments?type=delegated&service=$service&start-record=$startRecord&max-records=${appConfig.clientCountBatchSize}"))
        .willReturn(aResponse().withStatus(status).withBody(
          s"${Json.stringify(Json.toJson(enrolments))}"
    )))
  }

}
