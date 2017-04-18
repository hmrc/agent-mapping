package uk.gov.hmrc.agentmapping.connector

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentmapping.WSHttp
import uk.gov.hmrc.agentmapping.stubs.DesStubs
import uk.gov.hmrc.agentmapping.support.WireMockSupport
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import scala.concurrent.ExecutionContext.Implicits.global


class DesConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with DesStubs{
  private implicit val hc = HeaderCarrier()

  private val utr = "1234567890"
  private val bearerToken = "auth-token"
  private val environment = "des-env"

  override protected def expectedBearerToken = Some(bearerToken)
  override protected def expectedEnvironment = Some(environment)

  private lazy val connector: DesConnector =
    new DesConnector(environment, bearerToken, new URL(s"http://localhost:$wireMockPort"), WSHttp)

  "getRegistration" should {
    "return registration details for an individual UTR that is known by DES" in {
      individualRegistrationExists(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(Some(Arn("AARN0000002"))))
    }

    "return empty registration details for an individual UTR that is known by DES but has no associated ARN" in {
      individualRegistrationExistsWithoutArn(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(None))
    }

    "not return a registration for a UTR that is unknown to DES" in {
      registrationDoesNotExist(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe None
    }
  }
}
