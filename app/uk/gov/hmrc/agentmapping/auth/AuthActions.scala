/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.mvc._
import uk.gov.hmrc.agentmapping.model.Service.AgentCode
import uk.gov.hmrc.agentmapping.model.{Identifier, Service}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals.authorisedEnrolments
import uk.gov.hmrc.auth.core.retrieve.{Retrievals, ~}
import uk.gov.hmrc.play.HeaderCarrierConverter.fromHeadersAndSession
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

@Singleton
class AuthActions @Inject()(val authConnector: AuthConnector) extends AuthorisedFunctions with BaseController {

  private type HasEligibleEnrolments = Boolean
  private type ProviderId = String

  def AuthorisedWithEnrolments(body: Request[AnyContent] => HasEligibleEnrolments => Future[Result]): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc = fromHeadersAndSession(request.headers, None)

      authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
        .retrieve(Retrievals.allEnrolments) {
          case allEnrolments => body(request)(captureIdentifiersFrom(allEnrolments).nonEmpty)
        }
    }

  def AuthorisedWithAgentCode(body: Request[AnyContent] => Set[Identifier] => ProviderId => Future[Result])(
    handleError: PartialFunction[Throwable, Result]): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc = fromHeadersAndSession(request.headers, None)

    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
      .retrieve(Retrievals.credentials and Retrievals.agentCode and Retrievals.allEnrolments) {
        case credentials ~ agentCodeOpt ~ allEnrolments =>
          captureIdentifiersAndAgentCodeFrom(allEnrolments, agentCodeOpt) match {
            case Some(identifiers) => body(request)(identifiers)(credentials.providerId)

            case None => Future.successful(Forbidden)
          }
      }
      .recover { handleError }
  }

  def AuthorisedAsAgent(body: Request[AnyContent] => Arn => Future[Result])(
    handleError: PartialFunction[Throwable, Result]): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc = fromHeadersAndSession(request.headers, None)

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
      .recover { handleError }
  }

  def BasicAuth(body: Request[AnyContent] => Future[Result]): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc = fromHeadersAndSession(request.headers, None)

    authorised() {
      body(request)
    } recover {
      case _: NoActiveSession => Unauthorized
    }
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

  private def captureIdentifiersFrom(enrolments: Enrolments): Set[Identifier] =
    enrolments.enrolments
      .map(e => Service.valueOf(e.key).map((_, e.identifiers)))
      .collect {
        case Some((service, identifiers)) => Identifier(service, identifiers.map(i => i.value).mkString("/"))
      }
}
