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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Request}
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentmapping.audit.AuditService
import uk.gov.hmrc.agentmapping.model.Service._
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmapping.repository._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.{Retrievals, _}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter.fromHeadersAndSession
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.auth.core.retrieve.Retrievals.authorisedEnrolments

import scala.concurrent.Future

@Singleton
class MappingController @Inject()(
  repositories: MappingRepositories,
  auditService: AuditService,
  val authConnector: AuthConnector)
    extends BaseController
    with AuthorisedFunctions {

  import auditService._
  import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

  def hasEligibleEnrolments() = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
      .retrieve(Retrievals.allEnrolments) {
        case allEnrolments =>
          Future.successful(Ok(Json.obj("hasEligibleEnrolments" -> captureIdentifiersFrom(allEnrolments).nonEmpty)))
      }
  }

  def createMapping(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc = fromHeadersAndSession(request.headers, None)
    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
      .retrieve(Retrievals.credentials and Retrievals.agentCode and Retrievals.allEnrolments) {
        case credentials ~ agentCodeOpt ~ allEnrolments =>
          captureIdentifiersAndAgentCodeFrom(allEnrolments, agentCodeOpt) match {
            case Some(identifiers) =>
              identifiers
                .map(identifier => createMappingInRepository(arn, identifier, credentials.providerId))
                .reduce((f1, f2) => f1.flatMap(b1 => f2.map(b2 => b1 & b2)))
                .map(
                  allConflicted =>
                    if (allConflicted)
                      Conflict
                    else
                    Created)
            case None => Future.successful(Forbidden)
          }
      }
      .recover {
        case ex: NoActiveSession =>
          Logger(getClass).warn("No active session whilst trying to create mapping", ex)
          Unauthorized
        case ex: AuthorisationException =>
          Logger(getClass).warn("Authorisation exception whilst trying to create mapping", ex)
          Forbidden
      }
  }

  private def captureIdentifiersAndAgentCodeFrom(
    enrolments: Enrolments,
    agentCodeOpt: Option[String]): Option[Set[Identifier]] = {
    val identifiers = captureIdentifiersFrom(enrolments)
    if (identifiers.isEmpty) None
    else
      Some(agentCodeOpt match {
        case None            => identifiers
        case Some(agentCode) => identifiers + Identifier(AgentCode, agentCode)
      })
  }

  private def captureIdentifiersFrom(enrolments: Enrolments): Set[Identifier] =
    enrolments.enrolments
      .map(e => Service.valueOf(e.key).map((_, e.identifiers)))
      .collect {
        case Some((service, identifiers)) => Identifier(service, identifiers.map(i => i.value).mkString("/"))
      }

  private def createMappingInRepository(businessId: TaxIdentifier, identifier: Identifier, ggCredId: String)(
    implicit hc: HeaderCarrier,
    request: Request[Any]): Future[Boolean] =
    repositories
      .get(identifier.key)
      .store(businessId, identifier.value)
      .map { _ =>
        businessId match {
          case arn: Arn => sendCreateMappingAuditEvent(arn, identifier, ggCredId)
          case _        => ()
        }

        false
      }
      .recover {
        case e: DatabaseException if e.getMessage().contains("E11000") =>
          businessId match {
            case arn: Arn => sendCreateMappingAuditEvent(arn, identifier, ggCredId, duplicate = true)
            case _        => ()
          }

          Logger(getClass).warn(s"Duplicated mapping attempt for ${Service.asString(identifier.key)}")
          true
      }

  def findSaMappings(arn: uk.gov.hmrc.agentmtdidentifiers.model.Arn) = Action.async { implicit request =>
    repositories.get(`IR-SA-AGENT`).findBy(arn) map { matches =>
      implicit val writes: Writes[AgentReferenceMapping] = writeAgentReferenceMappingWith("saAgentReference")
      if (matches.nonEmpty) Ok(toJson(AgentReferenceMappings(matches))(Json.writes[AgentReferenceMappings]))
      else NotFound
    }
  }

  def findVatMappings(arn: uk.gov.hmrc.agentmtdidentifiers.model.Arn) = Action.async { implicit request =>
    repositories.get(`HMCE-VAT-AGNT`).findBy(arn) map { matches =>
      implicit val writes: Writes[AgentReferenceMapping] = writeAgentReferenceMappingWith("vrn")
      if (matches.nonEmpty) Ok(toJson(AgentReferenceMappings(matches))(Json.writes[AgentReferenceMappings]))
      else NotFound
    }
  }

  def findAgentCodeMappings(arn: uk.gov.hmrc.agentmtdidentifiers.model.Arn) = Action.async { implicit request =>
    repositories.get(AgentCode).findBy(arn) map { matches =>
      implicit val writes: Writes[AgentReferenceMapping] = writeAgentReferenceMappingWith("agentCode")
      if (matches.nonEmpty) Ok(toJson(AgentReferenceMappings(matches))(Json.writes[AgentReferenceMappings]))
      else NotFound
    }
  }

  def findMappings(key: String, arn: uk.gov.hmrc.agentmtdidentifiers.model.Arn) = Action.async { implicit request =>
    Service.forKey(key) match {
      case Some(service) =>
        repositories.get(service).findBy(arn) map { matches =>
          if (matches.nonEmpty) Ok(toJson(AgentReferenceMappings(matches))(Json.writes[AgentReferenceMappings]))
          else NotFound
        }
      case None =>
        Future.successful(BadRequest(s"No service found for the key $key"))
    }
  }

  def delete(arn: Arn) = Action.async { implicit request =>
    Future
      .sequence(repositories.map(_.delete(arn)))
      .map { _ =>
        NoContent
      }
  }

  def createPreSubscriptionMapping(utr: Utr) = Action.async { implicit request =>
    implicit val hc = fromHeadersAndSession(request.headers, None)
    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
      .retrieve(Retrievals.credentials and Retrievals.agentCode and Retrievals.allEnrolments) {
        case credentials ~ agentCodeOpt ~ allEnrolments =>
          captureIdentifiersAndAgentCodeFrom(allEnrolments, agentCodeOpt) match {
            case Some(identifiers) =>
              identifiers
                .map(identifier => createMappingInRepository(utr, identifier, credentials.providerId))
                .reduce((f1, f2) => f1.flatMap(b1 => f2.map(b2 => b1 & b2)))
                .map(
                  allConflicted =>
                    if (allConflicted)
                      Conflict
                    else
                    Created)
            case None => Future.successful(Forbidden)
          }
      }
      .recover {
        case ex: NoActiveSession =>
          Logger(getClass).warn("No active session whilst trying to create mapping", ex)
          Unauthorized
        case ex: AuthorisationException =>
          Logger(getClass).warn("Authorisation exception whilst trying to create mapping", ex)
          Forbidden
      }
  }

  def deletePreSubscriptionMapping(utr: Utr) = Action.async { implicit request =>
    Future
      .sequence(repositories.map(_.delete(utr)))
      .map { _ =>
        NoContent
      }
  }

  def createPostSubscriptionMapping(utr: Utr) = Action.async { implicit request =>
    authorised(
      Enrolment("HMRC-AS-AGENT")
        and AuthProviders(GovernmentGateway))
      .retrieve(authorisedEnrolments) { enrolments =>
        val arnOpt = getEnrolmentValue(enrolments, "HMRC-AS-AGENT", "AgentReferenceNumber")

        arnOpt match {
          case Some(arn) =>
            repositories.updateUtrToArn(Arn(arn), utr).map(_ => Ok)
          case None => Future.successful(Forbidden)
        }
      }
      .recover {
        case ex: NoActiveSession =>
          Logger(getClass).warn("No active session whilst trying to create mapping", ex)
          Unauthorized
        case ex: AuthorisationException =>
          Logger(getClass).warn("Authorisation exception whilst trying to create mapping", ex)
          Forbidden
      }
  }

  private def getEnrolmentValue(enrolments: Enrolments, serviceName: String, identifierKey: String) =
    for {
      enrolment  <- enrolments.getEnrolment(serviceName)
      identifier <- enrolment.getIdentifier(identifierKey)
    } yield identifier.value

  private def writeAgentReferenceMappingWith(identifierFieldName: String): Writes[AgentReferenceMapping] =
    Writes[AgentReferenceMapping](m =>
      Json.obj("arn" -> JsString(m.businessId.value), identifierFieldName -> JsString(m.identifier)))
}
