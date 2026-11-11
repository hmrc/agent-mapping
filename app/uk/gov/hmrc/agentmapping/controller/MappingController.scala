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

import play.api.libs.json.*
import play.api.libs.json.Json.toJson
import play.api.mvc.*
import uk.gov.hmrc.agentmapping.auth.AuthActions
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentmapping.model.*
import uk.gov.hmrc.agentmapping.repository.*
import uk.gov.hmrc.agentmapping.service.MappingService
import uk.gov.hmrc.agentmapping.util.RequestAwareLogging
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Singleton
import javax.inject.Inject
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

@Singleton
class MappingController @Inject() (
  repositories: MappingRepositories,
  espConnector: EnrolmentStoreProxyConnector,
  appConfig: AppConfig,
  mappingService: MappingService,
  cc: ControllerComponents,
  val authActions: AuthActions
)(
  implicit val ec: ExecutionContext
)
extends BackendController(cc)
with RequestAwareLogging:

  def hasEligibleEnrolments: Action[AnyContent] = authActions.authorisedWithEnrolments { implicit request => hasEligibleEnrolments =>
    Future.successful(Ok(Json.obj("hasEligibleEnrolments" -> hasEligibleEnrolments)))
  }
  end hasEligibleEnrolments

  def createMapping(arn: Arn): Action[AnyContent] = authActions.authorisedWithAgentCode { implicit request => identifiers => providerId =>
    for alreadyMapped <- mappingService.createMappings(
        arn,
        identifiers,
        isAutomapping = false,
        providerId
      )
    yield if alreadyMapped then Conflict else Created
  }

  def autoMapEnrolments(arn: Arn): Action[AnyContent] = authActions.authorisedAsSubscribedAgentWithGroup:
    implicit request =>
      authArn =>
        groupId =>
          providerId =>
            if authArn != arn then
              Future.successful(Forbidden)
            else
              for
                identifiers <- mappingService.getActiveIdentifiers(groupId)
                alreadyMapped <- mappingService.createMappings(
                  arn,
                  identifiers,
                  isAutomapping = true,
                  providerId
                )
              yield
                if identifiers.isEmpty then NoContent
                else if alreadyMapped then Conflict
                else Created

  def getClientCount: Action[AnyContent] = authActions.authorisedWithProviderId { implicit request => providerId =>
    espConnector
      .getClientCount(providerId)
      .map(clientCount => Ok(Json.obj("clientCount" -> clientCount)))
  }
  end getClientCount

  def findSaMappings(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    authActions.authorised():
      repositories.get(LegacyAgentEnrolmentType.IRAgentReference).findBy(arn) map { matches =>
        if matches.nonEmpty then
          Ok(toJson(AgentReferenceMappings(matches))(using AgentReferenceMappings.apiWrites("saAgentReference")))
        else
          NotFound
        end if

      }
  }
  end findSaMappings

  def findAgentCodeMappings(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    authActions.authorised():
      repositories.get(LegacyAgentEnrolmentType.AgentCode).findBy(arn) map { matches =>
        if matches.nonEmpty then
          Ok(toJson(AgentReferenceMappings(matches))(using AgentReferenceMappings.apiWrites("agentCode")))
        else
          NotFound
        end if

      }

  }

  end findAgentCodeMappings

  def findMappings(
    key: String,
    arn: Arn
  ): Action[AnyContent] = Action.async { implicit request =>
    authActions.authorised():

      LegacyAgentEnrolmentType.findByDataBaseKey(key) match
        case Some(service) =>
          repositories.get(service).findBy(arn) map { matches =>
            if matches.nonEmpty then
              Ok(toJson(AgentReferenceMappings(matches))(using AgentReferenceMappings.apiWrites()))
            else
              NotFound
            end if

          }
        case None => Future.successful(BadRequest(s"No service found for the key $key"))
      end match

  }
  end findMappings

  /** Allows a Stride user (with the appropriate role) to delete a mapping for a given enrolment type and value.
    *
    * @todo
    *   does not currently audit deletion events
    *
    * @param enrolment
    *   the enrolment service type (see [[LegacyAgentEnrolmentType]])
    * @param value
    *   the agent code (identifier)
    * @param arn
    *   agent reference number
    * @return
    *   204 if the mapping was deleted, 404 if it was not found
    */
  def deleteMapping(
    enrolment: LegacyAgentEnrolmentType,
    value: String,
    arn: Arn
  ): Action[AnyContent] =
    authActions.onlyStride(appConfig.strideConfig.unmappingRole): _ =>
      repositories.get(enrolment).deleteMapping(arn, value).map: deleted =>
        if deleted then NoContent else NotFound

end MappingController
