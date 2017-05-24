package uk.gov.hmrc.agentmapping.audit

import javax.inject.Inject

import play.api.mvc.Request
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.domain.{AgentCode, SaAgentReference}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.agentmapping.audit.AgentMappingEvent.AgentMappingEvent
import uk.gov.hmrc.play.audit.model.DataEvent
import scala.concurrent.ExecutionContext.Implicits.global


import scala.concurrent.Future
import scala.util.Try

object AgentMappingEvent extends Enumeration {

  val KnownFactsCheck = Value

  type AgentMappingEvent = AgentMappingEvent.Value
}


class AuditService @Inject()(val auditConnector: AuditConnector) {

  def auditEvent(event: AgentMappingEvent, transactionName: String, utr: Utr, arn: Arn, authCredId: Option[String], details: Seq[(String, Any)] = Seq.empty)
                (implicit hc: HeaderCarrier, request: Request[Any]): Future[Unit] = {
    send(createEvent(event, transactionName, utr, arn, authCredId, details:_* ))
  }

  private def createEvent(event: AgentMappingEvent, transactionName: String, utr: Utr, arn: Arn, authCredId: Option[String], details: (String, Any)*)
                         (implicit hc: HeaderCarrier, request: Request[Any]): DataEvent = {
    DataEvent(auditSource = "agent-mapping",
      auditType = event.toString,
      tags = hc.toAuditTags(transactionName, request.path),
      detail = hc.toAuditDetails("Authorization" -> hc.authorization.get.value, "utr" -> utr.value, "agentReferenceNumber" -> arn.value, "authProviderId" -> authCredId.getOrElse(""))
        ++ Map(details.map(pair => pair._1 -> pair._2.toString): _*)
    )
  }

  private def send(events: DataEvent*)(implicit hc: HeaderCarrier): Future[Unit] = {
    Future {
      events.foreach { event =>
        Try(auditConnector.sendEvent(event))
      }
    }
  }

}
