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

package uk.gov.hmrc.agentmapping.controller

import javax.inject.{ Inject, Singleton }

import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.mvc.{ Action, AnyContent, Request }
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentmapping.audit.AuditService
import uk.gov.hmrc.agentmapping.connector.DesConnector
import uk.gov.hmrc.agentmapping.model.Names._
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmapping.repository._
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{ Retrievals, _ }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter.fromHeadersAndSession
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

class UnsupportedIdentifierKey(identifier: Identifier) extends Exception(s"Unsupported identifier key ${identifier.key}")

@Singleton
class MappingController @Inject() (
  vatAgentReferenceMappingRepository: VatAgentReferenceMappingRepository,
  saAgentReferenceMappingRepository: SaAgentReferenceMappingRepository,
  agentCodeMappingRepository: AgentCodeMappingRepository,
  desConnector: DesConnector,
  auditService: AuditService,
  val authConnector: AuthConnector)
  extends BaseController with AuthorisedFunctions {

  import auditService._
  import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

  private val validEnrolmentIdentifierKeys: Map[String, String] = Map(
    IRAgentReference -> `IR-SA-AGENT`,
    AgentRefNo -> `HMCE-VAT-AGNT`)

  def enrolmentsFor(identifiers: Identifiers): Predicate = {
    identifiers.get
      .map(i => Enrolment(
        validEnrolmentIdentifierKeys.getOrElse(i.key, throw new UnsupportedIdentifierKey(i))).withIdentifier(i.key, i.value)).reduce[Predicate](_ and _)
  }

  def createMapping(utr: Utr, arn: Arn, identifiers: Identifiers): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc = fromHeadersAndSession(request.headers, None)
    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent and enrolmentsFor(identifiers))
      .retrieve(Retrievals.credentials and Retrievals.agentCode) {
        case credentials ~ agentCodeOpt =>
          desConnector.getArn(utr) flatMap {
            case Some(Arn(arn.value)) =>
              sendKnownFactsCheckAuditEvent(utr, arn, credentials.providerId, matched = true)
              withAgentCode(identifiers, agentCodeOpt).map(
                identifier => createMappingInRepository(arn, identifier, credentials.providerId))
                .reduce((f1, f2) => f1.flatMap(b1 => f2.map(b2 => b1 & b2)))
                .map(
                  allConflicted => if (allConflicted)
                    Conflict
                  else
                    Created)

            case _ =>
              sendKnownFactsCheckAuditEvent(utr, arn, credentials.providerId, matched = false)
              Future.successful(Forbidden)
          }
      }.recoverWith {
        case ex: NoActiveSession =>
          Logger.warn("No active session whilst trying to create mapping", ex)
          Future.successful(Unauthorized)
        case ex: AuthorisationException =>
          Logger.warn("Authorisation exception whilst trying to create mapping", ex)
          Future.successful(Forbidden)
        case ex: UnsupportedIdentifierKey =>
          Logger.warn("An attempt to do mapping with an invalid identifier", ex)
          Future.successful(Forbidden)
      }
  }

  private def withAgentCode(identifiers: Identifiers, agentCodeOpt: Option[String]) = agentCodeOpt match {
    case None => identifiers.get
    case Some(agentCode) => identifiers.get :+ Identifier(AgentCode, agentCode)
  }

  def createMappingInRepository(arn: Arn, identifier: Identifier, ggCredId: String)(implicit hc: HeaderCarrier, request: Request[Any]): Future[Boolean] = {
    repository(identifier.key)
      .createMapping(arn, identifier.value)
      .map { _ =>
        sendCreateMappingAuditEvent(arn, identifier, ggCredId)
        false
      }
      .recover {
        case e: DatabaseException if e.getMessage().contains("E11000") =>
          sendCreateMappingAuditEvent(arn, identifier, ggCredId, duplicate = true)
          Logger.warn(s"Duplicated mapping attempt for ${identifier.key}")
          true
      }
  }

  val repository: Map[String, MappingRepository] = Map(
    IRAgentReference -> saAgentReferenceMappingRepository,
    AgentRefNo -> vatAgentReferenceMappingRepository,
    AgentCode -> agentCodeMappingRepository)

  def findSaMappings(arn: uk.gov.hmrc.agentmtdidentifiers.model.Arn) = Action.async { implicit request =>
    saAgentReferenceMappingRepository.findBy(arn) map { matches =>
      if (matches.nonEmpty) Ok(toJson(SaAgentReferenceMappings(matches))) else NotFound
    }
  }

  def findVatMappings(arn: uk.gov.hmrc.agentmtdidentifiers.model.Arn) = Action.async { implicit request =>
    vatAgentReferenceMappingRepository.findBy(arn) map { matches =>
      if (matches.nonEmpty) Ok(toJson(VatAgentReferenceMappings(matches))) else NotFound
    }
  }

  def findAgentCodeMappings(arn: uk.gov.hmrc.agentmtdidentifiers.model.Arn) = Action.async { implicit request =>
    agentCodeMappingRepository.findBy(arn) map { matches =>
      if (matches.nonEmpty) Ok(toJson(AgentCodeMappings(matches))) else NotFound
    }
  }

  def delete(arn: Arn) = Action.async { implicit request =>
    saAgentReferenceMappingRepository.delete(arn)
      .andThen {
        case _ => vatAgentReferenceMappingRepository.delete(arn)
      }
      .andThen {
        case _ => agentCodeMappingRepository.delete(arn)
      }
      .map { _ => NoContent }
  }
}
