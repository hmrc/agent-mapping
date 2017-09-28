package uk.gov.hmrc.agentmapping.connector

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentmapping.WSHttp
import uk.gov.hmrc.agentmapping.stubs.DesStubs
import uk.gov.hmrc.agentmapping.support.{MetricTestSupport, WireMockSupport}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global


class DesConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with DesStubs with MetricTestSupport {
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
      givenIndividualRegistrationExists(utr)
      givenCleanMetricRegistry()

      val registration = await(connector.getArn(utr))

      registration shouldBe Some(registeredArn)

      timerShouldExistsAndBeenUpdated("ConsumedAPI-DES-RegistrationIndividualUtr-POST")
    }

    "return None for an individual UTR that is known by DES but has no associated ARN" in {
      givenIndividualRegistrationExistsWithoutArn(utr)
      givenCleanMetricRegistry()

      val registration = await(connector.getArn(utr))

      registration shouldBe None

      timerShouldExistsAndBeenUpdated("ConsumedAPI-DES-RegistrationIndividualUtr-POST")
    }

    "not return None for a UTR that is unknown to DES" in {
      givenRegistrationDoesNotExist(utr)
      givenCleanMetricRegistry()

      val registration = await(connector.getArn(utr))

      registration shouldBe None

      timerShouldExistsAndBeenUpdated("ConsumedAPI-DES-RegistrationIndividualUtr-POST")
    }
  }
}
