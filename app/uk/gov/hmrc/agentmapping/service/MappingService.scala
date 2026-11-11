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

package uk.gov.hmrc.agentmapping.service

import play.api.mvc.Request
import uk.gov.hmrc.agentmapping.audit.AuditService
import uk.gov.hmrc.agentmapping.connector.AgentServicesAccountConnector
import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.model.Enrolment
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolmentType
import uk.gov.hmrc.agentmapping.model.ServiceIdentifier
import uk.gov.hmrc.agentmapping.repository.MappingRepositories
import uk.gov.hmrc.agentmapping.util.RequestAwareLogging
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.mongo.lock.TimePeriodLockService

import java.util.UUID
import javax.inject.Singleton
import javax.inject.Inject
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

@Singleton
class MappingService @Inject() (
  auditService: AuditService,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  agentServicesAccountConnector: AgentServicesAccountConnector,
  repositories: MappingRepositories,
  mongoLockRepository: MongoLockRepository
)(using ExecutionContext)
extends RequestAwareLogging:

  /** Retrieves the group's enrolments, retaining active enrolments with recognised service types, and returns their identifiers.
    *
    * @param groupId
    *   the enrolment store group identifier
    * @return
    *   the set of service identifiers extracted from the group's active enrolments
    */
  def getActiveIdentifiers(groupId: String)(using Request[?]): Future[Set[ServiceIdentifier]] =
    enrolmentStoreProxyConnector
      .getPrincipalEnrolments(groupId)
      .map(_.enrolments.filter(_.state.equalsIgnoreCase("Activated")))
      .map(MappingService.extractServiceIdentifiers)

  /** Attempt to add a mapping to the database and send an audit event if successful. If the mapping already exists, it will not be added again, but an audit
    * event will still be sent.
    *
    * @param arn
    *   agent reference number
    * @param identifier
    *   agent code
    * @param providerId
    *   the user ID (for audit purposes)
    * @param isAutomapping
    *   true if initiated by the auto-mapping process, false if initiated by a user
    * @return
    *   true if the mapping already exists, false if it was added successfully
    */
  def createMapping(
    arn: Arn,
    identifier: ServiceIdentifier,
    isAutomapping: Boolean = false,
    providerId: String
  )(using Request[Any]): Future[Boolean] = repositories
    .get(identifier.enrolmentType)
    .store(
      arn,
      identifier.value,
      automapped = isAutomapping
    )
    .map: isDuplicate =>
      auditService.sendCreateMappingAuditEvent(
        arn,
        identifier,
        providerId,
        duplicate = isDuplicate,
        automapped = isAutomapping
      )

      if isDuplicate && !isAutomapping then
        logger.warn(s"Duplicated mapping attempt for ${identifier.enrolmentType}")

      isDuplicate

  /** Attempt to add multiple mappings to the database and send audit events if successful. If a mapping already exists, it will not be added again, but an
    * audit event will still be sent.
    *
    * @param arn
    *   agent reference number
    * @param identifiers
    *   agent codes
    * @param isAutomapping
    *   true if initiated by the auto-mapping process, false if initiated by a user
    * @param providerId
    *   the user ID (for audit purposes)
    *
    * @return
    *   true if all mappings already exist, false if at least one was added successfully
    */
  def createMappings(
    arn: Arn,
    identifiers: Set[ServiceIdentifier],
    isAutomapping: Boolean,
    providerId: String
  )(using Request[?]): Future[Boolean] =
    for results <-
        Future.traverse(identifiers): identifier =>
          createMapping(
            arn,
            identifier,
            isAutomapping,
            providerId
          )
    yield results.isEmpty || !results.contains(false)

  // We cannot distinguish which ARN is correct, so we check if any of the ARNs are suspended in case there's multiple
  def checkSuspensions(arns: Set[Arn])(using request: Request[Any]): Future[Boolean] =
    Future.traverse(arns)(agentServicesAccountConnector.isSuspended).map(_.contains(true))

  def isUserAlreadyNotified(using request: Request[Any]): Future[Boolean] =
    TimePeriodLockService(
      mongoLockRepository,
      request.headers.get(SessionKeys.sessionId).getOrElse(UUID.randomUUID().toString),
      30.minutes
    ).withRenewedLock(Future.unit).map:
      case Some(_) => false
      case None => true

  // TODO make return of findMappedArns an Option[Arn] instead of Seq[Arn] once the mapping uniqueness is enforced
  def findMappedArnsAndAttemptAutomapping(
    identifier: ServiceIdentifier,
    groupId: String,
    credId: String
  )(using request: Request[Any]): Future[Set[Arn]] =
    repositories.getAllArnsByServiceIdentifier(identifier).flatMap:
      case arns if arns.nonEmpty =>
        // no automapping here as this is the primary path for the status check and for performance reasons we don't want to trigger automapping on every status check
        if arns.size > 1 then
          logger.warn(s"Multiple ARNs found for ${identifier.toString}: ${arns.mkString(", ")}")
        Future.successful(arns.toSet)
      case Nil =>
        findDirectMapping(groupId, identifier).flatMap:
          case None => Future.successful(Set.empty)
          case Some(arn) =>
            createMapping(
              arn,
              identifier,
              isAutomapping = true,
              credId
            )
              .map(_ => Set(arn))

  end findMappedArnsAndAttemptAutomapping

  private def findDirectMapping(
    groupId: String,
    identifier: ServiceIdentifier
  )(using Request[?]) =
    for
      uniqueIdentifiers <- getActiveIdentifiers(groupId)
      otherIdentifiers = uniqueIdentifiers.filter(_ != identifier)
      results <- Future.traverse(otherIdentifiers)(repositories.getAllArnsByServiceIdentifier)
    yield
      val uniqueArns = results.flatten
      val hasConflicts = uniqueArns.sizeIs > 1
      val directMapping = uniqueArns.headOption.filterNot(_ => hasConflicts)

      if hasConflicts then
        // TODO Audit?
        warnAboutConflicts(identifier, uniqueArns)

      directMapping
  end findDirectMapping

  private def warnAboutConflicts(
    identifier: ServiceIdentifier,
    uniqueArns: Set[Arn]
  )(using Request[?]): Unit =
    logger.warn(s"Mapping check failure: agent has no direct mapping for identifier ${identifier.toString}" +
      s" but they have other identifier mappings for different ARNs," +
      s" this prevents us from automapping as we do not know which ARN is right ${uniqueArns.mkString(", ")}")

end MappingService

object MappingService:

  def extractServiceIdentifiers(enrolments: Seq[Enrolment]): Set[ServiceIdentifier] =
    for
      enrolment <- enrolments.toSet
      service <- LegacyAgentEnrolmentType.findByName(enrolment.service).toSet
      identifier <- enrolment.identifiers
    yield ServiceIdentifier(service, identifier.value)

end MappingService
