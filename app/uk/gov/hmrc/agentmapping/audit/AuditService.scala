/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentmapping.audit

import javax.inject.Inject

import com.google.inject.Singleton
import play.api.mvc.Request
import uk.gov.hmrc.agentmapping.audit.AgentMappingEvent.AgentMappingEvent
import uk.gov.hmrc.agentmapping.model.Identifier
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import scala.util.Try
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.agentmapping.model.Names._

object AgentMappingEvent extends Enumeration {

  val KnownFactsCheck, CreateMapping = Value

  type AgentMappingEvent = AgentMappingEvent.Value
}

@Singleton
class AuditService @Inject() (val auditConnector: AuditConnector) {

  def sendKnownFactsCheckAuditEvent(utr: Utr, arn: Arn, authProviderId: String, matched: Boolean)(implicit hc: HeaderCarrier, request: Request[Any]): Unit = {
    auditEvent(AgentMappingEvent.KnownFactsCheck, "known-facts-check", Seq("authProviderId" -> authProviderId, "knownFactsMatched" -> matched, "utr" -> utr.value, "agentReferenceNumber" -> arn.value))
  }

  def sendCreateMappingAuditEvent(arn: Arn, identifier: Identifier, authProviderId: String, duplicate: Boolean = false)(implicit hc: HeaderCarrier, request: Request[Any]): Unit = {
    identifier.key match {
      case IRAgentReference =>
        auditEvent(AgentMappingEvent.CreateMapping, "create-mapping", Seq("authProviderId" -> authProviderId, "saAgentRef" -> identifier.value, "agentReferenceNumber" -> arn.value, "duplicate" -> duplicate))
      case AgentRefNo =>
        auditEvent(AgentMappingEvent.CreateMapping, "create-mapping", Seq("authProviderId" -> authProviderId, "vatAgentRef" -> identifier.value, "agentReferenceNumber" -> arn.value, "duplicate" -> duplicate))
      case AgentCode =>
        auditEvent(AgentMappingEvent.CreateMapping, "create-mapping", Seq("authProviderId" -> authProviderId, "agentCode" -> identifier.value, "agentReferenceNumber" -> arn.value, "duplicate" -> duplicate))
    }
  }

  private[audit] def auditEvent(event: AgentMappingEvent, transactionName: String, details: Seq[(String, Any)] = Seq.empty)(implicit hc: HeaderCarrier, request: Request[Any]): Future[Unit] = {
    send(createEvent(event, transactionName, details: _*))
  }

  private def createEvent(event: AgentMappingEvent, transactionName: String, details: (String, Any)*)(implicit hc: HeaderCarrier, request: Request[Any]): DataEvent = {
    DataEvent(
      auditSource = "agent-mapping",
      auditType = event.toString,
      tags = hc.toAuditTags(transactionName, request.path),
      detail = hc.toAuditDetails(details.map(pair => pair._1 -> pair._2.toString): _*))
  }

  private def send(events: DataEvent*)(implicit hc: HeaderCarrier): Future[Unit] = {
    Future {
      events.foreach { event =>
        Try(auditConnector.sendEvent(event))
      }
    }
  }

}
