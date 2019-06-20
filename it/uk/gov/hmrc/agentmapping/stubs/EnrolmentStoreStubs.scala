package uk.gov.hmrc.agentmapping.stubs


import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json
import uk.gov.hmrc.agentmapping.connector.{Enrolment, EnrolmentResponse}

trait EnrolmentStoreStubs {

  private def clientListUrl(service: String, userId: String, startRecord: Int, maxClientSize: Int): String = {
    s"enrolment-store-proxy/enrolment-store/users/$userId/enrolments?type=delegated&service=$service&start-record=$startRecord&max-records=$maxClientSize"
  }

  def givenEs2ClientsFoundFor(userId: String,
                              service: String,
                              startRecord: Int,
                              enrolments: EnrolmentResponse, status: Int) = {

    import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector.writes
    stubFor(
      get(urlEqualTo(s"/enrolment-store-proxy/enrolment-store/users/$userId/enrolments?type=delegated&service=$service&start-record=$startRecord&max-records=5"))
        .willReturn(aResponse().withStatus(status).withBody(
          s"${Json.stringify(Json.toJson(enrolments))}"
    )))
  }

}
