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

import play.api.libs.json.*
import play.api.mvc.*
import uk.gov.hmrc.agentmapping.auth.AuthActions
import uk.gov.hmrc.agentmapping.model.*
import uk.gov.hmrc.agentmapping.repository.*
import uk.gov.hmrc.agentmapping.util.RequestAwareLogging
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class MappingLookupController @Inject() (
  repositories: MappingRepositories,
  authActions: AuthActions,
  cc: ControllerComponents
)(using ExecutionContext)
extends BackendController(cc)
with RequestAwareLogging:

  def mappingsByArn(arn: Arn): Action[AnyContent] = Action.async:
    case given Request[?] =>
      authActions.authorised():
        for results <- repositories.getAllIdentifiersBy(arn)
        yield Ok(Json.toJson(enrolmentKeyToIdentifiers(results)))

  def mappingsByKeyAndAgentCode(
    enrolmentKey: String,
    agentCode: String
  ): Action[AnyContent] = Action.async:
    case given Request[?] =>
      authActions.authorised():
        val repository =
          LegacyAgentEnrolmentType.findByDataBaseKey(enrolmentKey) match
            case None => throw new NotFoundException(s"Enrolment key $enrolmentKey not found")
            case Some(legacyAgentEnrolmentType) => repositories.get(legacyAgentEnrolmentType)

        for arns <- repository.findArnBy(agentCode)
        yield Ok(Json.toJson(arns))

  def mappingsByAgentCode(code: String): Action[AnyContent] = Action.async:
    case given Request[?] =>
      authActions.authorised():
        for results <- repositories.getAllArnsBy(code)
        yield Ok(Json.toJson(enrolmentKeyToIdentifiers(results)))

  private def enrolmentKeyToIdentifiers[A](results: Map[LegacyAgentEnrolmentType, Seq[A]]) =
    val withoutNewAgentCodes = results - LegacyAgentEnrolmentType.AgentCode

    for
      (enrolmentType, identifiers) <- withoutNewAgentCodes
      if identifiers.nonEmpty
    yield enrolmentType.enrolmentKey -> identifiers
