/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.agentmapping.auth
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.agentmapping.model.{AgentCode, Identifier, LegacyAgentEnrolmentType}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{agentCode, allEnrolments, authorisedEnrolments, credentials}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter.fromHeadersAndSession
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthActions @Inject()(val authConnector: AuthConnector, cc: ControllerComponents)
    extends BackendController(cc)
    with AuthorisedFunctions {

  private type HasEligibleEnrolments = Boolean
  private type ProviderId = String

  def authorisedWithEnrolments(body: Request[AnyContent] => HasEligibleEnrolments => Future[Result])(
    implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)

      authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
        .retrieve(allEnrolments)(allEnrolments => body(request)(captureIdentifiersFrom(allEnrolments).nonEmpty))
    }

  def authorisedWithAgentCode(body: Request[AnyContent] => Set[Identifier] => ProviderId => Future[Result])(
    implicit ec: ExecutionContext): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)

    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
      .retrieve(credentials and agentCode and allEnrolments) {
        case Some(Credentials(providerId, _)) ~ agentCodeOpt ~ allEnrolments =>
          captureIdentifiersAndAgentCodeFrom(allEnrolments, agentCodeOpt) match {
            case Some(identifiers) => body(request)(identifiers)(providerId)

            case None => Future.successful(Forbidden)
          }
      }
      .recover { handleException }
  }

  def authorisedWithProviderId(body: Request[AnyContent] => String => Future[Result])(
    implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
        .retrieve(credentials) {
          case Some(Credentials(providerId, _)) => body(request)(providerId)
          case _                                => Future.successful(Forbidden)
        }
        .recover { handleException }
    }

  def authorisedAsSubscribedAgent(body: Request[AnyContent] => Arn => Future[Result])(
    implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)

      authorised(
        Enrolment("HMRC-AS-AGENT")
          and AuthProviders(GovernmentGateway))
        .retrieve(authorisedEnrolments) { enrolments =>
          val arnOpt = getEnrolmentInfo(enrolments.enrolments, "HMRC-AS-AGENT", "AgentReferenceNumber")

          arnOpt match {
            case Some(arn) => body(request)(Arn(arn))
            case None      => Future.successful(Forbidden)
          }
        }
        .recover { handleException }
    }

  def basicAuth(body: Request[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)

      authorised() {
        body(request)
      } recover {
        case _: NoActiveSession => Unauthorized
      }
    }

  def onlyStride(strideRole: String)(body: Request[AnyContent] => Future[Result])(
    implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthProviders(PrivilegedApplication))
        .retrieve(allEnrolments) {
          case allEnrols if allEnrols.enrolments.map(_.key).contains(strideRole) =>
            body(request)
          case e: Enrolments =>
            Logger(getClass).warn(
              s"Unauthorized Discovered during Stride Authentication: ${e.enrolments.map(enrol => enrol.key).mkString(",")}")
            Future successful Unauthorized
        }
        .recover {
          case e =>
            Logger(getClass).warn(s"Error Discovered during Stride Authentication: ${e.getMessage}")
            Forbidden
        }
    }

  private def handleException(implicit ec: ExecutionContext, request: Request[_]): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession        => Unauthorized
    case _: AuthorisationException => Forbidden
  }

  private def getEnrolmentInfo(enrolment: Set[Enrolment], enrolmentKey: String, identifier: String): Option[String] =
    enrolment.find(_.key equals enrolmentKey).flatMap(_.identifiers.find(_.key equals identifier).map(_.value))

  private def captureIdentifiersAndAgentCodeFrom(
    enrolments: Enrolments,
    agentCodeOpt: Option[String]): Option[Set[Identifier]] = {
    val identifiers = captureIdentifiersFrom(enrolments)
    if (identifiers.isEmpty) None
    else
      Some(agentCodeOpt match {
        case None            => identifiers
        case Some(agentCode) => identifiers + Identifier(AgentCode, agentCode)
      })
  }

  private def captureIdentifiersFrom(enrolments: Enrolments): Set[Identifier] = {

    case class AgentEnrolment(legacyAgentEnrolmentType: LegacyAgentEnrolmentType, values: Seq[String])

    enrolments.enrolments
      .map(
        enrolment =>
          LegacyAgentEnrolmentType
            .find(enrolment.key)
            .map(eType => AgentEnrolment(eType, enrolment.identifiers.map(_.value))))
      .collect {
        // We only use the FIRST value; since all the enrolments we care about are single valued
        case Some(AgentEnrolment(enrolmentType, Seq(enrolmentIdentifierValue))) =>
          Identifier(enrolmentType, enrolmentIdentifierValue)
      }
  }

}
