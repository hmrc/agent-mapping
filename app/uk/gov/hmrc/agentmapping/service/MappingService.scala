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

import com.mongodb.MongoWriteException
import play.api.mvc.Request
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmapping.audit.AuditService
import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.model.Identifier
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolmentType
import uk.gov.hmrc.agentmapping.repository.MappingRepositories
import uk.gov.hmrc.agentmapping.util.RequestAwareLogging

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class MappingService @Inject() (
  auditService: AuditService,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  repositories: MappingRepositories
)(using ExecutionContext)
extends RequestAwareLogging:

  def getGroupIdentifiers(groupId: String)(using
    rh: RequestHeader
  ): Future[Seq[Identifier]] =
    enrolmentStoreProxyConnector.getPrincipalEnrolments(groupId).map: response =>
      response.enrolments
        .filter(_.state.equalsIgnoreCase("Activated"))
        .flatMap: enrolment =>
          LegacyAgentEnrolmentType
            .findByName(enrolment.service)
            .fold(Seq.empty[Identifier]): service =>
              enrolment.identifiers.map(id =>
                Identifier(service, id.value)
              ).toSet

  def createMappingInRepository(
    arn: Arn,
    identifier: Identifier,
    ggCredId: String,
    isAutomapping: Boolean = false
  )(implicit
    request: Request[Any]
  ): Future[Boolean] = repositories
    .get(identifier.enrolmentType)
    .store(arn, identifier.value)
    .map { _ =>
      auditService.sendCreateMappingAuditEvent(
        arn,
        identifier,
        ggCredId,
        automapped = isAutomapping
      )
      false
    }.recover:
      case e: MongoWriteException if e.getMessage.contains("E11000") && isAutomapping => true
      case e: MongoWriteException if e.getMessage.contains("E11000") =>
        auditService.sendCreateMappingAuditEvent(
          arn,
          identifier,
          ggCredId,
          duplicate = true
        )
        logger.warn(s"Duplicated mapping attempt for ${identifier.enrolmentType}")
        true

end MappingService
