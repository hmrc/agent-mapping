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
import play.api.mvc.Request
import play.api.mvc.ControllerComponents
import play.api.mvc.AnyContent
import play.api.mvc.Action
import uk.gov.hmrc.agentmapping.auth.AuthActions
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.controller.MappingStatusController.*
import uk.gov.hmrc.agentmapping.model.ServiceIdentifier
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolmentType
import uk.gov.hmrc.agentmapping.service.MappingService
import uk.gov.hmrc.agentmapping.util.RequestAwareLogging
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import java.time.Clock
import javax.inject.Singleton
import javax.inject.Inject
import scala.concurrent.ExecutionContext

@Singleton
class MappingStatusController @Inject() (
  cc: ControllerComponents,
  val authActions: AuthActions,
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
      case request @ given Request[AnyContent] =>
        for
          arns <- mappingService.findMappedArnsAndAttemptAutomapping(
            identifier,
            request.groupId,
            request.credId
          )
          mappedArnIsSuspended <- mappingService.checkSuspensions(arns)
          missedDeadline = appConfig.mandationDateFor(enrolment).exists(!LocalDate.now(clock).isBefore(_))
          userAlreadyNotified <- mappingService.isUserAlreadyNotified
        yield
          if arns.nonEmpty then NoContent
          else
            if mappedArnIsSuspended then Locked(Json.toJson(MappingStatusResponse("suspension page url")))
            else if missedDeadline then NotFound(Json.toJson(MappingStatusResponse("mapping journey url")))
            else Ok(Json.toJson(MappingStatusResponse("mapping journey/notification url", Some(userAlreadyNotified))))

  end mappingStatus

end MappingStatusController

object MappingStatusController:

  case class MappingStatusResponse(
    continueUrl: String,
    alreadyNotified: Option[Boolean] = None
  )
  given OWrites[MappingStatusResponse] = Json.writes[MappingStatusResponse]

end MappingStatusController
