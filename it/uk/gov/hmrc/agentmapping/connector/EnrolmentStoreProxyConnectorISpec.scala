package uk.gov.hmrc.agentmapping.connector

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.metrics.Metrics
import uk.gov.hmrc.agentmapping.stubs.EnrolmentStoreStubs
import uk.gov.hmrc.agentmapping.support.{BaseISpec, WireMockSupport}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global

class EnrolmentStoreProxyConnectorISpec  extends BaseISpec with WireMockSupport with GuiceOneAppPerSuite with EnrolmentStoreStubs{

  private lazy implicit val metrics = app.injector.instanceOf[Metrics]
  private lazy val http = app.injector.instanceOf[HttpClient]

  private val clientCountBatchSize = 7
  private val clientCountMaxRecords = 40

  override implicit lazy val app: Application = appBuilder.configure("clientCount.maxRecord" -> clientCountMaxRecords, "clientCount.batchSize" -> clientCountBatchSize)
    .build()

  private lazy val appConfig = app.injector.instanceOf[AppConfig]


  implicit val hc = HeaderCarrier()

  private lazy val connector: EnrolmentStoreProxyConnector =
    new EnrolmentStoreProxyConnector(appConfig, http, metrics)


  "runEs2ForServices" should {
    "return the total count for VAT and SA" in {

      val maxSizeResponse = EnrolmentResponse(List.fill(clientCountBatchSize)(Enrolment("Activated")))
      val partialSizeResponse = EnrolmentResponse(List.fill(clientCountBatchSize - 2)(Enrolment("Activated")))


      def callEs2EndpointForService(serviceName: String, startRecord: Int) = givenEs2ClientsFoundFor("agent1", serviceName, startRecord, 200)

      givenEs2ClientsFoundFor("agent1","HMCE-VATDEC-ORG",1,maxSizeResponse,200)
      givenEs2ClientsFoundFor("agent1","HMCE-VATDEC-ORG",6,maxSizeResponse,200)
      givenEs2ClientsFoundFor("agent1","HMCE-VATDEC-ORG",11,partialSizeResponse,200)
      givenEs2ClientsFoundFor("agent1","IR-SA",1,maxSizeResponse,200)
      givenEs2ClientsFoundFor("agent1","IR-SA",6,maxSizeResponse,200)
      givenEs2ClientsFoundFor("agent1","IR-SA",11,maxSizeResponse,200)
      givenEs2ClientsFoundFor("agent1","IR-SA",16,partialSizeResponse,200)

      await(connector.getClientCount("agent1")) shouldBe 30
    }
  }
}
