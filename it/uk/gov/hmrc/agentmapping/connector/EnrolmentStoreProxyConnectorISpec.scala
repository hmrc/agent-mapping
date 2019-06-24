package uk.gov.hmrc.agentmapping.connector

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentmapping.stubs.EnrolmentStoreStubs
import uk.gov.hmrc.agentmapping.support.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class EnrolmentStoreProxyConnectorISpec  extends UnitSpec with WireMockSupport with OneAppPerSuite with EnrolmentStoreStubs{

  private lazy implicit val metrics = app.injector.instanceOf[Metrics]
  private lazy val http = app.injector.instanceOf[HttpGet]

  override implicit lazy val app: Application = appBuilder.build()

  implicit val hc = HeaderCarrier()

  private lazy val connector: EnrolmentStoreProxyConnector =
    new EnrolmentStoreProxyConnector(new URL(s"http://localhost:$wireMockPort"),
      5, http)

  protected def appBuilder: GuiceApplicationBuilder = {
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.enrolment-store-proxy.host" -> wireMockHost,
        "microservice.services.enrolment-store-proxy.port" -> wireMockPort,
        "clientCount.maxRecords" -> 5
      )
  }


  "runEs2ForServices" should {
    "return the total count for VAT and SA" in {

      val maxSizeResponse = EnrolmentResponse(List.fill(5)(Enrolment("Activated")))
      val partialSizeResponse = EnrolmentResponse(List.fill(3)(Enrolment("Activated")))

      givenEs2ClientsFoundFor("agent1","HMCE-VATDEC-ORG",1,maxSizeResponse,200)
      givenEs2ClientsFoundFor("agent1","HMCE-VATDEC-ORG",6,maxSizeResponse,200)
      givenEs2ClientsFoundFor("agent1","HMCE-VATDEC-ORG",11,maxSizeResponse,200)
      givenEs2ClientsFoundFor("agent1","HMCE-VATDEC-ORG",16,maxSizeResponse,200)
      givenEs2ClientsFoundFor("agent1","HMCE-VATDEC-ORG",21,partialSizeResponse,200)
      givenEs2ClientsFoundFor("agent1","IR-SA",1,partialSizeResponse,200)

      await(connector.getClientCount("agent1")) shouldBe 26
    }
  }
}
