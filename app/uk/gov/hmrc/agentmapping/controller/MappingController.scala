/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.mvc.{Action, AnyContent, Request, Result}
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentmapping.audit.AuditService
import uk.gov.hmrc.agentmapping.auth.AuthActions
import uk.gov.hmrc.agentmapping.connector.{EnrolmentStoreProxyConnector, SubscriptionConnector}
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmapping.repository._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MappingController @Inject()(
  repositories: MappingRepositories,
  auditService: AuditService,
  subscriptionConnector: SubscriptionConnector,
  espConnector: EnrolmentStoreProxyConnector,
  val authActions: AuthActions)(implicit val ec: ExecutionContext)
    extends BaseController {

  import auditService._
  import authActions._

  def hasEligibleEnrolments: Action[AnyContent] =
    authorisedWithEnrolments { implicit request => hasEligibleEnrolments =>
      Future.successful(Ok(Json.obj("hasEligibleEnrolments" -> hasEligibleEnrolments)))
    }

  def createMapping(arn: Arn): Action[AnyContent] =
    authorisedWithAgentCode { implicit request => identifiers => implicit providerId =>
      createMapping(arn, identifiers)
    }

  def createMappingsFromSubscriptionJourneyRecord(arn: Arn): Action[AnyContent] =
    authorisedWithProviderId { implicit request => implicit providerId =>
      for {
        userMappings <- subscriptionConnector.getUserMappings(AuthProviderId(providerId))
        createMappingsResult <- userMappings match {
                                 case Some(mappings) =>
                                   val identifiers = createIdentifiersFromUserMappings(mappings)
                                   createMapping(arn, identifiers)
                                 case None =>
                                   Logger.error(
                                     "no subscription journey record found when attempting to create mappings")
                                   Future successful NoContent
                               }
      } yield createMappingsResult
    }

  def getClientCount: Action[AnyContent] =
    authorisedWithProviderId { implicit request => providerId =>
      espConnector
        .getClientCount(providerId)
        .map(clientCount => Ok(Json.obj("clientCount" -> clientCount)))
    }

  /**
    * Add mapping for the passed identifier against an ARN or UTR (businessId)
    * The identifier key (enrolment type) determines which data store to use
    * Returns true IF the mapping already exists, false otherwise
    */
  private def createMappingInRepository(businessId: TaxIdentifier, identifier: Identifier, ggCredId: String)(
    implicit hc: HeaderCarrier,
    request: Request[Any]): Future[Boolean] =
    repositories
      .get(identifier.enrolmentType)
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
          Logger(getClass).warn(s"Duplicated mapping attempt for ${identifier.enrolmentType}")
          true
      }

  def findSaMappings(arn: uk.gov.hmrc.agentmtdidentifiers.model.Arn): Action[AnyContent] = Action.async {
    implicit request =>
      repositories.get(IRAgentReference).findBy(arn) map { matches =>
        implicit val writes: Writes[AgentReferenceMapping] = writeAgentReferenceMappingWith("saAgentReference")
        if (matches.nonEmpty) Ok(toJson(AgentReferenceMappings(matches))(Json.writes[AgentReferenceMappings]))
        else NotFound
      }
  }

  def findVatMappings(arn: uk.gov.hmrc.agentmtdidentifiers.model.Arn): Action[AnyContent] = Action.async {
    implicit request =>
      repositories.get(AgentRefNo).findBy(arn) map { matches =>
        implicit val writes: Writes[AgentReferenceMapping] = writeAgentReferenceMappingWith("vrn")
        if (matches.nonEmpty) Ok(toJson(AgentReferenceMappings(matches))(Json.writes[AgentReferenceMappings]))
        else NotFound
      }
  }

  def findAgentCodeMappings(arn: uk.gov.hmrc.agentmtdidentifiers.model.Arn): Action[AnyContent] =
    Action.async { implicit request =>
      repositories.get(AgentCode).findBy(arn) map { matches =>
        implicit val writes: Writes[AgentReferenceMapping] = writeAgentReferenceMappingWith("agentCode")
        if (matches.nonEmpty) Ok(toJson(AgentReferenceMappings(matches))(Json.writes[AgentReferenceMappings]))
        else NotFound
      }
    }

  def findMappings(key: String, arn: uk.gov.hmrc.agentmtdidentifiers.model.Arn): Action[AnyContent] =
    Action.async { implicit request =>
      LegacyAgentEnrolmentType.findByDbKey(key) match {
        case Some(service) =>
          repositories.get(service).findBy(arn) map { matches =>
            if (matches.nonEmpty) Ok(toJson(AgentReferenceMappings(matches))(Json.writes[AgentReferenceMappings]))
            else NotFound
          }
        case None =>
          Future.successful(BadRequest(s"No service found for the key $key"))
      }
    }

  def delete(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    Future
      .sequence(repositories.map(_.delete(arn)))
      .map { _ =>
        NoContent
      }
  }

  def createPreSubscriptionMapping(utr: Utr): Action[AnyContent] =
    authorisedWithAgentCode { implicit request => identifiers => implicit providerId =>
      createMapping(utr, identifiers)
    }

  def deletePreSubscriptionMapping(utr: Utr): Action[AnyContent] = basicAuth { implicit request =>
    Future
      .sequence(repositories.map(_.delete(utr)))
      .map { _ =>
        NoContent
      }
  }

  def createPostSubscriptionMapping(utr: Utr): Action[AnyContent] =
    authorisedAsSubscribedAgent { implicit request => arn =>
      repositories.updateUtrToArn(arn, utr).map(_ => Ok)
    }

  private def createMapping(businessId: TaxIdentifier, identifiers: Set[Identifier])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[AnyContent],
    providerId: String): Future[Result] = {

    val mappingResults: Set[Future[Boolean]] = identifiers
      .map(identifier => createMappingInRepository(businessId, identifier, providerId))

    Future
      .sequence(mappingResults)
      .map(resultSet => if (resultSet.contains(false)) Created else Conflict)
  }

  private def createIdentifiersFromUserMappings(userMappings: List[UserMapping]): Set[Identifier] = {

    val agentCodeIdentifiers: Set[Identifier] = userMappings
      .flatMap(userMapping => userMapping.agentCode.map(ac => Identifier(AgentCode, ac.value)))
      .toSet

    val legacyIdentifiers: Set[Identifier] = (for {
      userMapping <- userMappings
      enrolment   <- userMapping.legacyEnrolments
      service     <- LegacyAgentEnrolmentType.find(enrolment.enrolmentType.key)
      enrolmentIdentifier = Identifier(service, enrolment.identifierValue.value)
    } yield enrolmentIdentifier).toSet

    agentCodeIdentifiers ++ legacyIdentifiers
  }

  private def writeAgentReferenceMappingWith(identifierFieldName: String): Writes[AgentReferenceMapping] =
    Writes[AgentReferenceMapping](m =>
      Json.obj("arn" -> JsString(m.businessId.value), identifierFieldName -> JsString(m.identifier)))
}
