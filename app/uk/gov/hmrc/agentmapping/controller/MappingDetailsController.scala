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

import play.api.Logging
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.Request
import uk.gov.hmrc.agentmapping.auth.AuthActions
import uk.gov.hmrc.agentmapping.connector.SubscriptionConnector
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmapping.repository.MappingDetailsRepository
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDateTime
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class MappingDetailsController @Inject() (
  repository: MappingDetailsRepository,
  val authActions: AuthActions,
  cc: ControllerComponents,
  subscriptionConnector: SubscriptionConnector
)(implicit val ec: ExecutionContext)
extends BackendController(cc)
with Logging {

  import authActions._

  def createOrUpdateRecord(arn: Arn): Action[AnyContent] = authorisedAsSubscribedAgent { request: Request[AnyContent] => arnFromEnrolments =>
    implicit val jsonRequest: Request[JsValue] = request.map(_.asJson
      .getOrElse(throw new BadRequestException(s"Invalid content or content type, not a Json")))
    withJsonBody[MappingDetailsRequest] { mappingDetailsRequest =>
      for {
        _ <- requireArnMatchesFromEnrolments(arnFromRequest = arn, arnFromEnrolments = arnFromEnrolments)
        details: MappingDetails = MappingDetails(
          mappingDetailsRequest.authProviderId,
          mappingDetailsRequest.ggTag,
          mappingDetailsRequest.count,
          LocalDateTime.now()
        )
        maybeMappingDetailsRepositoryRecord <- repository.findByArn(arn)
        result <-
          maybeMappingDetailsRepositoryRecord match {
            case Some(record) =>
              if (record.mappingDetails.exists(m => m.authProviderId == mappingDetailsRequest.authProviderId)) {
                logger.error("already mapped")
                Future successful Conflict
              }
              else
                repository.updateMappingDisplayDetails(arn, details).map(_ => Ok)
            case None =>
              val record = MappingDetailsRepositoryRecord(arn, Seq(details))
              repository.create(record).map(_ => Created)
          }
      } yield result
    }
  }

  def findRecordByArn(arn: Arn): Action[AnyContent] = authorisedAsSubscribedAgent { request => arnFromEnrolments =>
    for {
      _ <- requireArnMatchesFromEnrolments(arnFromRequest = arn, arnFromEnrolments = arnFromEnrolments)
      result <- repository.findByArn(arn).map {
        case Some(record) => Ok(Json.toJson(record))
        case None => NotFound(s"no record found for this arn: $arn")
      }
    } yield result
  }

  def transferSubscriptionRecordToMappingDetails(arn: Arn): Action[AnyContent] = authorisedWithProviderId { implicit request => implicit providerId =>
    subscriptionConnector.getUserMappings(AuthProviderId(providerId)).flatMap {
      case Some(userMappings) if userMappings.nonEmpty =>
        val record = MappingDetailsRepositoryRecord(arn, userMappings2MappingDetails(userMappings))
        repository.create(record).map(_ => Created)

      case Some(_) => Future successful Ok("No user mappings found")
      case None => Future successful NotFound(s"no user mappings found for this auth provider id: $providerId")
    }
  }

  def userMappings2MappingDetails(userMappings: List[UserMapping]): Seq[MappingDetails] = userMappings.map(u =>
    MappingDetails(
      u.authProviderId,
      GGTag(u.ggTag),
      u.count,
      LocalDateTime.now()
    )
  )

}
