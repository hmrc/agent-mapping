/*
 * Copyright 2026 HM Revenue & Customs
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

import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.Request
import uk.gov.hmrc.agentmapping.auth.AuthActions
import uk.gov.hmrc.agentmapping.connector.AgentServicesAccountConnector
import uk.gov.hmrc.agentmapping.controller.MappingStatusController.*
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.model.ServiceIdentifier
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolmentType
import uk.gov.hmrc.agentmapping.repository.MappingRepositories
import uk.gov.hmrc.agentmapping.util.RequestAwareLogging
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.service.MappingService
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.mongo.lock.TimePeriodLockService

import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.Seq
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

@Singleton
class MappingStatusController @Inject() (
  cc: ControllerComponents,
  val authActions: AuthActions,
  mappingRepositories: MappingRepositories,
  mongoLockRepository: MongoLockRepository,
  agentServicesAccountConnector: AgentServicesAccountConnector,
  mappingService: MappingService,
  appConfig: AppConfig,
  clock: Clock
)(using ExecutionContext)
extends BackendController(cc)
with RequestAwareLogging:

  def mappingStatus(
    enrolment: LegacyAgentEnrolmentType,
    value: String
  ): Action[AnyContent] =
    val identifier = ServiceIdentifier(enrolment, value)
    // Flow:
    // 1. Verify the authenticated user holds the requested enrolment key/code.
    // 2. Look up any mapped ARNs for that identifier, attempting automated mapping using other identifiers of the agent group when missing.
    // 3. If a mapped ARN exists, return 204 unless any mapped ARN is suspended, in which case return 423.
    // 4. If no mapping exists and the deadline has passed, return 404.
    // 5. If no mapping exists before the deadline, return 200 with notification state depending on whether the lock was acquired.
    // 6. If no mapping exists and in the process of automapping two ARNs were found for different identifiers of the agent group, return 404 if the deadline has passed and 200 otherwise.

    authActions.authorisedAgentFor(identifier):
      implicit request =>
        findMappedArnsAndAttemptAutomapping(
          identifier,
          request.groupId,
          request.credId
        ).flatMap:
          case arns if arns.nonEmpty =>
            // We cannot distinguish which ARN is correct, so we check if any of the ARNs are suspended in case there's multiple
            // TODO make return of findMappedArns an Option[Arn] instead of Seq[Arn] once the mapping uniqueness is enforced
            Future.traverse(arns)(arn => agentServicesAccountConnector.isSuspended(arn)).map:
              case result if result.contains(true) =>
                // Mapped ARN is suspended
                Locked(Json.toJson(MappingStatusResponse("suspension page url")))
              case _ =>
                // Mapping is present and ARN is not suspended
                NoContent
          case Nil if appConfig.mandationDateFor(enrolment).exists(!LocalDate.now(clock).isBefore(_)) =>
            // No mapping present after deadline
            Future.successful(NotFound(Json.toJson(MappingStatusResponse("mapping journey url"))))
          case _ =>
            isUserAlreadyNotified.map: alreadyNotified =>
              Ok(Json.toJson(MappingStatusResponse("mapping journey/notification url", Some(alreadyNotified))))
  end mappingStatus

  private def isUserAlreadyNotified(using request: Request[Any]): Future[Boolean] =
    TimePeriodLockService(
      mongoLockRepository,
      request.headers.get(SessionKeys.sessionId).getOrElse(UUID.randomUUID().toString),
      30.minutes
    ).withRenewedLock(Future.unit).map:
      case Some(_) => false
      case None => true

  private def findMappedArnsAndAttemptAutomapping(
    identifier: ServiceIdentifier,
    groupId: String,
    credId: String
  )(using request: Request[Any]): Future[Seq[Arn]] =
    mappingRepositories.getAllArnsByServiceIdentifier(identifier).flatMap:
      case arns if arns.nonEmpty =>
        // no automapping here as this is the primary path for the status check and for performance reasons we don't want to trigger automapping on every status check
        if arns.size > 1 then
          logger.warn(s"Multiple ARNs found for ${identifier.toString}: ${arns.mkString(", ")}")
        Future.successful(arns)
      case Nil =>
        mappingService.getGroupIdentifiers(groupId).flatMap: identifiers =>
          Future.traverse(identifiers.filter(_ != identifier))(otherIdentifier =>
            mappingRepositories.getAllArnsByServiceIdentifier(otherIdentifier)
          ).map(_.flatten.distinct).flatMap:
            case arn :: Nil =>
              mappingService.createMappingInRepository(
                arn,
                identifier,
                credId,
                isAutomapping = true
              ).map(_ => Seq(arn))
            case arns if arns.size > 1 =>
              logger.warn(s"Mapping check failure: agent has no direct mapping for identifier ${identifier.toString}" +
                s" but they have other identifier mappings for different ARNs," +
                s" this prevents us from automapping as we do not know which ARN is right ${arns.mkString(", ")}")
              // TODO Audit?
              Future.successful(Nil)
            case Nil => Future.successful(Nil)
  end findMappedArnsAndAttemptAutomapping

end MappingStatusController

object MappingStatusController:

  case class MappingStatusResponse(
    continueUrl: String,
    alreadyNotified: Option[Boolean] = None
  )
  given OWrites[MappingStatusResponse] = Json.writes[MappingStatusResponse]

end MappingStatusController
