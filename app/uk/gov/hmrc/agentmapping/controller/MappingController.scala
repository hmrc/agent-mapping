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

package uk.gov.hmrc.agentmapping.controller

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.libs.json.Format
import play.api.libs.json.Json.{format, toJson}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentmapping.audit.AuditService
import uk.gov.hmrc.agentmapping.connector.DesConnector
import uk.gov.hmrc.agentmapping.repository.{Mapping, MappingRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.auth.core.authorise.{AffinityGroup, Enrolment}
import uk.gov.hmrc.auth.core.retrieve.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions, NoActiveSession}
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class MappingController @Inject()(mappingRepository: MappingRepository, desConnector: DesConnector, auditService: AuditService, val authConnector: AuthConnector)
  extends BaseController with AuthorisedFunctions {

  import auditService._
  import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

  def createMapping(utr: Utr, arn: Arn, saAgentReference: SaAgentReference): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent and Enrolment("IR-SA-AGENT").withIdentifier("saAgentReference", utr.value))
      .retrieve(Retrievals.authProviderId) {
        case ggCredId: GGCredId =>
          desConnector.getArn(utr) flatMap {
            case Some(Arn(arn.value)) =>
              sendKnownFactsCheckAuditEvent(utr, arn, ggCredId.credId, matched = true)
              mappingRepository
                .createMapping(arn, saAgentReference)
                .map { _ =>
                  sendCreateMappingAuditEvent(arn, saAgentReference, ggCredId.credId)
                  Created
                }
                .recover {
                  case e: DatabaseException if e.getMessage().contains("E11000") =>
                  sendCreateMappingAuditEvent(arn, saAgentReference, ggCredId.credId, duplicate = true)
                  Conflict
              }

          case _ =>
            sendKnownFactsCheckAuditEvent(utr, arn, ggCredId.credId, matched = false)
            Future.successful(Forbidden)
        }
    }.recoverWith {
      case ex: NoActiveSession        =>
        Logger.warn("No active session whilst trying to create mapping", ex)
        Future.successful(Unauthorized)
      case ex: AuthorisationException =>
        Logger.warn("Authorisation exception whilst trying to create mapping", ex)
        Future.successful(Forbidden)
    }
  }

  def findMappings(arn: uk.gov.hmrc.agentmtdidentifiers.model.Arn) = Action.async { implicit request =>
    mappingRepository.findBy(arn) map { matches =>
      if (matches.nonEmpty) Ok(toJson(Mappings(matches))) else NotFound
    }
  }

  def delete(arn: Arn) = Action.async { implicit request =>
    mappingRepository.delete(arn) map { _ => NoContent }
  }
}

case class Mappings(mappings: List[Mapping])

object Mappings extends ReactiveMongoFormats {
  implicit val formats: Format[Mappings] = format[Mappings]
}
