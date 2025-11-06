/*
 * Copyright 2023 HM Revenue & Customs
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

import com.mongodb.MongoWriteException
import play.api.Logging
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.agentmapping.audit.AuditService
import uk.gov.hmrc.agentmapping.auth.AuthActions
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentmapping.connector.SubscriptionConnector
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmapping.repository._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class MappingController @Inject() (
  appConfig: AppConfig,
  repositories: MappingRepositories,
  detailsRepository: MappingDetailsRepository,
  auditService: AuditService,
  subscriptionConnector: SubscriptionConnector,
  espConnector: EnrolmentStoreProxyConnector,
  cc: ControllerComponents,
  val authActions: AuthActions
)(implicit val ec: ExecutionContext)
extends BackendController(cc)
with Logging {

  import auditService._
  import authActions._

  def hasEligibleEnrolments: Action[AnyContent] = authorisedWithEnrolments { request => hasEligibleEnrolments =>
    Future.successful(Ok(Json.obj("hasEligibleEnrolments" -> hasEligibleEnrolments)))
  }

  def createMapping(arn: Arn): Action[AnyContent] = authorisedWithAgentCode { implicit request => identifiers => implicit providerId =>
    createMapping(arn, identifiers)
  }

  def createMappingsFromSubscriptionJourneyRecord(arn: Arn): Action[AnyContent] = authorisedWithProviderId { implicit request => implicit providerId =>
    for {
      userMappings <- subscriptionConnector.getUserMappings(AuthProviderId(providerId))
      createMappingsResult <-
        userMappings match {
          case Some(mappings) =>
            val identifiers = createIdentifiersFromUserMappings(mappings)
            createMapping(arn, identifiers)
          case None =>
            logger.error(
              "no subscription journey record found when attempting to create mappings"
            )
            Future successful NoContent
        }
    } yield createMappingsResult
  }

  def getClientCount: Action[AnyContent] = authorisedWithProviderId { implicit request => providerId =>
    espConnector
      .getClientCount(providerId)
      .map(clientCount => Ok(Json.obj("clientCount" -> clientCount)))
  }

  /** Add mapping for the passed identifier against an ARN. The identifier key (enrolment type) determines which data store to use Returns true IF the mapping
    * already exists, false otherwise
    */
  private def createMappingInRepository(
    arn: Arn,
    identifier: Identifier,
    ggCredId: String
  )(implicit
    request: Request[Any]
  ): Future[Boolean] = repositories
    .get(identifier.enrolmentType)
    .store(arn, identifier.value)
    .map { _ =>
      sendCreateMappingAuditEvent(
        arn,
        identifier,
        ggCredId
      )
      false
    }
    .recover {
      case e: MongoWriteException if e.getMessage().contains("E11000") =>
        sendCreateMappingAuditEvent(
          arn,
          identifier,
          ggCredId,
          duplicate = true
        )
        logger.warn(s"Duplicated mapping attempt for ${identifier.enrolmentType}")
        true
    }

  def findSaMappings(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    authActions.authorised() {
      repositories.get(IRAgentReference).findBy(arn) map { matches =>
        implicit val writes: Writes[AgentReferenceMapping] = writeAgentReferenceMappingWith("saAgentReference")
        if (matches.nonEmpty)
          Ok(toJson(AgentReferenceMappings(matches))(Json.writes[AgentReferenceMappings]))
        else
          NotFound
      }
    }
  }

  def findAgentCodeMappings(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    authActions.authorised() {
      repositories.get(AgentCode).findBy(arn) map { matches =>
        implicit val writes: Writes[AgentReferenceMapping] = writeAgentReferenceMappingWith("agentCode")
        if (matches.nonEmpty)
          Ok(toJson(AgentReferenceMappings(matches))(Json.writes[AgentReferenceMappings]))
        else
          NotFound
      }
    }
  }

  def findMappings(
    key: String,
    arn: Arn
  ): Action[AnyContent] = Action.async { implicit request =>
    authActions.authorised() {
      LegacyAgentEnrolmentType.findByDbKey(key) match {
        case Some(service) =>
          repositories.get(service).findBy(arn) map { matches =>
            if (matches.nonEmpty)
              Ok(toJson(AgentReferenceMappings(matches))(Json.writes[AgentReferenceMappings]))
            else
              NotFound
          }
        case None => Future.successful(BadRequest(s"No service found for the key $key"))
      }
    }
  }

  def removeMappingsForAgent(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth(appConfig.expectedAuth) {
      (for {
        mappingRecords <- repositories.deleteDataForArn(arn).map(_.sum)
        detailRecords <- detailsRepository.removeMappingDetailsForAgent(arn)
      } yield Ok(
        Json.toJson[TerminationResponse](
          TerminationResponse(
            Seq(DeletionCount(
              appConfig.appName,
              "all-regimes",
              mappingRecords.toInt + detailRecords
            ))
          )
        )
      )).recover { case e =>
        logger.warn(s"Something has gone for $arn due to: ${e.getMessage}")
        InternalServerError
      }
    }
  }

  private def createMapping(
    arn: Arn,
    identifiers: Set[Identifier]
  )(implicit
    ec: ExecutionContext,
    request: Request[AnyContent],
    providerId: String
  ): Future[Result] = {

    val mappingResults: Set[Future[Boolean]] = identifiers
      .map(identifier =>
        createMappingInRepository(
          arn,
          identifier,
          providerId
        )
      )

    Future
      .sequence(mappingResults)
      .map(resultSet =>
        if (resultSet.contains(false))
          Created
        else
          Conflict
      )
  }

  private def createIdentifiersFromUserMappings(userMappings: List[UserMapping]): Set[Identifier] = {

    val agentCodeIdentifiers: Set[Identifier] =
      userMappings
        .flatMap(userMapping => userMapping.agentCode.map(ac => Identifier(AgentCode, ac.value)))
        .toSet

    val legacyIdentifiers: Set[Identifier] =
      (for {
        userMapping <- userMappings
        enrolment <- userMapping.legacyEnrolments
        service <- LegacyAgentEnrolmentType.find(enrolment.enrolmentType.key)
        enrolmentIdentifier = Identifier(service, enrolment.identifierValue.value)
      } yield enrolmentIdentifier).toSet

    agentCodeIdentifiers ++ legacyIdentifiers
  }

  private def writeAgentReferenceMappingWith(identifierFieldName: String): Writes[AgentReferenceMapping] = Writes[AgentReferenceMapping](m =>
    Json.obj("arn" -> JsString(m.arn.value), identifierFieldName -> JsString(m.identifier))
  )

}
