package uk.gov.hmrc.agentmapping.support


import com.google.inject.AbstractModule
import com.kenshoo.play.metrics.PlayModule
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentmapping.model.MappingDetailsRepositoryRecord

abstract class ServerBaseISpec extends BaseISpec with GuiceOneServerPerSuite with ScalaFutures {

  override implicit lazy val app: Application = appBuilder.build()

  override protected def appBuilder: GuiceApplicationBuilder = {
    new GuiceApplicationBuilder()
      .disable[PlayModule]
      .configure(
        Map(
          "microservice.services.auth.port" -> wireMockPort,
          "microservice.services.agent-subscription.port" -> wireMockPort,
          "microservice.services.agent-subscription.host" -> wireMockHost,
          "auditing.consumer.baseUri.host" -> wireMockHost,
          "auditing.consumer.baseUri.port" -> wireMockPort,
          "application.router" -> "testOnlyDoNotUseInAppConf.Routes",
          "migrate-repositories" -> "false",
          "termination.stride.enrolment" -> "caat"
        ))
      .overrides(new TestGuiceModule)
  }

  protected class TestGuiceModule extends AbstractModule {
    override def configure(): Unit = {}
  }

  def matchRecordIgnoringDateTime(
                                   mappingDisplayRecord: MappingDetailsRepositoryRecord): Matcher[MappingDetailsRepositoryRecord] =
    new Matcher[MappingDetailsRepositoryRecord] {
      override def apply(left: MappingDetailsRepositoryRecord): MatchResult = left match {
        case record
          if mappingDisplayRecord.arn == record.arn &&
            mappingDisplayRecord.mappingDetails.map(m => (m.ggTag, m.count)) == record.mappingDetails
              .map(m => (m.ggTag, m.count)) =>
          MatchResult(matches = true, "", "")
      }
    }

}

