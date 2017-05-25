/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.agentmapping.audit.AgentMappingEvent.AgentMappingEvent
import uk.gov.hmrc.agentmapping.connector.AuthConnector
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object AgentMappingEvent extends Enumeration {

  val KnownFactsCheck = Value

  type AgentMappingEvent = AgentMappingEvent.Value
}

@Singleton
class AuditService @Inject()(val auditConnector: AuditConnector, val authConnector: AuthConnector) {

  def sendKnownFactsCheckAuditEvent(utr: Utr, arn: Arn, matched: Boolean)
                                   (implicit hc: HeaderCarrier, request: Request[Any]): Unit = {
    auditEvent(AgentMappingEvent.KnownFactsCheck, "known-facts-check", utr, arn, Seq("knownFactsMatched" -> matched))
  }

  def auditEvent(event: AgentMappingEvent, transactionName: String, utr: Utr, arn: Arn, details: Seq[(String, Any)] = Seq.empty)
                (implicit hc: HeaderCarrier, request: Request[Any]): Future[Unit] = {
     authConnector.currentAuthDetails() flatMap {
       case authDetailsOpt =>
         send(createEvent(event, transactionName, utr, arn, authDetailsOpt.flatMap(_.ggCredentialId), details: _*))
     }
  }

  private def createEvent(event: AgentMappingEvent, transactionName: String, utr: Utr, arn: Arn, authCredId: Option[String], details: (String, Any)*)
                         (implicit hc: HeaderCarrier, request: Request[Any]): DataEvent = {
    DataEvent(auditSource = "agent-mapping",
      auditType = event.toString,
      tags = hc.toAuditTags(transactionName, request.path),
      detail = hc.toAuditDetails(details.map(pair => pair._1 -> pair._2.toString): _*)
        ++ Map("utr" -> utr.value, "agentReferenceNumber" -> arn.value)
        ++ authCredId.map(id => Map("authProviderId" -> id)).getOrElse(Seq.empty)
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
