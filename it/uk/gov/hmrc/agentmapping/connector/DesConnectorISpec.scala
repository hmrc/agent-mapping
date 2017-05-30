package uk.gov.hmrc.agentmapping.connector

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentmapping.WSHttp
import uk.gov.hmrc.agentmapping.stubs.DesStubs
import uk.gov.hmrc.agentmapping.support.WireMockSupport
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global


class DesConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with DesStubs{
  private implicit val hc = HeaderCarrier()

  private val utr = Utr("1234567890")
  private val bearerToken = "auth-token"
  private val environment = "des-env"

  override protected def expectedBearerToken = Some(bearerToken)
  override protected def expectedEnvironment = Some(environment)

  private lazy val connector: DesConnector =
    new DesConnector(environment, bearerToken, new URL(s"http://localhost:$wireMockPort"), WSHttp, app.injector.instanceOf[Metrics])

  "getRegistration" should {

    "return an arn for an individual UTR that is known by DES" in {
      individualRegistrationExists(utr)

      val metricsRegistry = app.injector.instanceOf[Metrics].defaultRegistry
      val registration = await(connector.getArn(utr))

      registration shouldBe Some(registeredArn)

      metricsRegistry.getTimers.get("Timer-ConsumedAPI-DES-RegistrationIndividualUtr-POST").getCount should be >= 1L
    }

    "return None for an individual UTR that is known by DES but has no associated ARN" in {
      individualRegistrationExistsWithoutArn(utr)

      val metricsRegistry = app.injector.instanceOf[Metrics].defaultRegistry
      val registration = await(connector.getArn(utr))

      registration shouldBe None


      metricsRegistry.getTimers.get("Timer-ConsumedAPI-DES-RegistrationIndividualUtr-POST").getCount should be >= 1L
    }

    "not return None for a UTR that is unknown to DES" in {
      registrationDoesNotExist(utr)

      val metricsRegistry = app.injector.instanceOf[Metrics].defaultRegistry
      val registration = await(connector.getArn(utr))

      registration shouldBe None

      metricsRegistry.getTimers.get("Timer-ConsumedAPI-DES-RegistrationIndividualUtr-POST").getCount should be >= 1L
    }
  }
}
