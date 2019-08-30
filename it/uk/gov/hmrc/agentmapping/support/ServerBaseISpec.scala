package uk.gov.hmrc.agentmapping.support


import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import uk.gov.hmrc.agentmapping.model.MappingDetailsRepositoryRecord

abstract class ServerBaseISpec extends BaseISpec with GuiceOneServerPerSuite with ScalaFutures {

  override implicit lazy val app: Application = appBuilder.build()

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(20, Seconds), interval = Span(1, Seconds))

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

