package uk.gov.hmrc.agentmapping.support

import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.agentmapping.stubs.DataStreamStub
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.test.UnitSpec

abstract class BaseISpec extends UnitSpec with WireMockSupport with DataStreamStub with MetricTestSupport with ScalaFutures {

  def app: Application

  protected def appBuilder: GuiceApplicationBuilder = {
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "microservice.services.enrolment-store-proxy.port" -> wireMockPort,
        "microservice.services.agent-subscription.port" -> wireMockPort,
        "metrics.enabled" -> true,
        "auditing.enabled" -> true,
        "clientCount.maxRecords" -> 40,
        "clientCount.batchSize" -> 7,
        "mongodb.uri" -> "mongodb://localhost:27017/test-agent-mapping",
        "auditing.consumer.baseUri.port"                -> wireMockPort,
        "migrate-repositories"                          -> "false",
        "termination.stride.enrolment"                  -> "caat"
      )
  }

  override def commonStubs(): Unit = {
    givenCleanMetricRegistry()
    givenAuditConnector()
    ()
  }

  protected implicit val materializer = app.materializer


  private val messagesApi = app.injector.instanceOf[MessagesApi]
  private implicit val messages: Messages = messagesApi.preferred(Seq.empty[Lang])

  protected def htmlEscapedMessage(key: String): String = HtmlFormat.escape(Messages(key)).toString

  implicit def hc(implicit request: FakeRequest[_]): HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))


}
