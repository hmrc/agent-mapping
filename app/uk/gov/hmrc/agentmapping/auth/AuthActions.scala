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

package uk.gov.hmrc.agentmapping.auth

import play.api.mvc.*
import uk.gov.hmrc.agentmapping.model.ServiceIdentifier
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolmentType
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.util.RequestAwareLogging
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.*
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Singleton
import javax.inject.Inject
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

@Singleton
class AuthActions @Inject() (
  val authConnector: AuthConnector,
  cc: ControllerComponents
)
extends BackendController(cc)
with AuthorisedFunctions
with RequestAwareLogging:

  private type HasEligibleEnrolments = Boolean
  private type ProviderId = String

  def authorisedWithEnrolments(
    body: Request[AnyContent] => HasEligibleEnrolments => Future[Result]
  )(
    implicit ec: ExecutionContext
  ): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
      .retrieve(allEnrolments)(allEnrolments => body(request)(captureIdentifiersFrom(allEnrolments).nonEmpty))
      .recover { case _: AuthorisationException => Unauthorized }
  }
  end authorisedWithEnrolments

  case class AuthorisedAgentRequest[A](
    groupId: String,
    credId: String,
    request: Request[A]
  )
  extends WrappedRequest[A](request)

  def authorisedAgentFor(identifier: ServiceIdentifier)(
    body: AuthorisedAgentRequest[AnyContent] => Future[Result]
  )(
    implicit ec: ExecutionContext
  ): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
      .retrieve(allEnrolments and groupIdentifier and credentials):
        case enrolments ~ Some(groupIdentifier) ~ Some(Credentials(credId, _)) =>
          if captureIdentifiersFrom(enrolments).contains(identifier) then
            body(AuthorisedAgentRequest(
              groupIdentifier,
              credId,
              request
            ))
          else
            logger.warn(s"error - agent does not have enrolment for ${identifier.enrolmentType} with value ${identifier.value}")
            Future.successful(Forbidden)
        case _ => Future.successful(Forbidden)
      .recover:
        case _: NoActiveSession => Unauthorized
        case _: AuthorisationException => Forbidden
  }
  end authorisedAgentFor

  def authorisedWithAgentCode(
    body: Request[AnyContent] => Set[ServiceIdentifier] => ProviderId => Future[Result]
  )(
    implicit ec: ExecutionContext
  ): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
      .retrieve(credentials and agentCode and allEnrolments) {
        case Some(Credentials(providerId, _)) ~ agentCodeOpt ~ allEnrolments =>

          captureIdentifiersAndAgentCodeFrom(allEnrolments, agentCodeOpt) match
            case Some(identifiers) => body(request)(identifiers)(providerId)
            case _ => Future.successful(Forbidden)
          end match

        case _ =>
          logger.warn(s"error - missing credentials")
          Future.successful(Forbidden)
      }.recover:
        case _: NoActiveSession => Unauthorized
        case _: AuthorisationException => Forbidden
  }
  end authorisedWithAgentCode

  def authorisedWithProviderId(
    body: Request[AnyContent] => String => Future[Result]
  )(
    implicit ec: ExecutionContext
  ): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway) and AffinityGroup.Agent)
      .retrieve(credentials) {
        case Some(Credentials(providerId, _)) => body(request)(providerId)
        case _ => Future.successful(Forbidden)
      }.recover:
        case _: NoActiveSession => Unauthorized
        case _: AuthorisationException => Forbidden
  }
  end authorisedWithProviderId

  def authorisedAsSubscribedAgentWithGroup(
    body: Request[AnyContent] => Arn => String => String => Future[Result]
  )(
    implicit ec: ExecutionContext
  ): Action[AnyContent] = Action.async { implicit request =>
    authorised(
      Enrolment("HMRC-AS-AGENT") and AuthProviders(GovernmentGateway)
    ).retrieve(authorisedEnrolments and groupIdentifier and credentials) {
      case enrolments ~ maybeGroupId ~ maybeCreds =>

        val maybeArn = getEnrolmentInfo(
          enrolments.enrolments,
          "HMRC-AS-AGENT",
          "AgentReferenceNumber"
        )

        (maybeArn, maybeGroupId, maybeCreds) match
          case (Some(arn), Some(groupId), Some(creds)) => body(request)(Arn(arn))(groupId)(creds.providerId)
          case _ => Future.successful(Forbidden)
        end match

    }.recover:
      case _: NoActiveSession => Unauthorized
      case _: AuthorisationException => Forbidden
  }
  end authorisedAsSubscribedAgentWithGroup

  def basicAuth(
    body: Request[AnyContent] => Future[Result]
  )(
    implicit ec: ExecutionContext
  ): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      body(request)
    } recover { case _: NoActiveSession => Unauthorized }
  }
  end basicAuth

  def onlyStride(
    strideRole: String
  )(
    body: Request[AnyContent] => Future[Result]
  )(
    implicit ec: ExecutionContext
  ): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(PrivilegedApplication))
      .retrieve(allEnrolments):
        case allEnrols if allEnrols.enrolments.map(_.key).contains(strideRole) => body(request)
        case e =>
          logger.warn(
            s"Unauthorized Discovered during Stride Authentication: ${e.enrolments.map(enrol => enrol.key).mkString(",")}"
          )
          Future successful Unauthorized
      .recover { case e =>
        logger.warn(s"Error Discovered during Stride Authentication: ${e.getMessage}")
        Forbidden
      }
  }
  end onlyStride

  private def getEnrolmentInfo(
    enrolment: Set[Enrolment],
    enrolmentKey: String,
    identifier: String
  ): Option[String] = enrolment
    .find(_.key.equals(enrolmentKey))
    .flatMap(
      _.identifiers
        .find(_.key.equals(identifier))
        .map(_.value)
    )
  end getEnrolmentInfo

  private def captureIdentifiersAndAgentCodeFrom(
    enrolments: Enrolments,
    agentCodeOpt: Option[String]
  ): Option[Set[ServiceIdentifier]] =
    val identifiers = captureIdentifiersFrom(enrolments)
    if identifiers.isEmpty then
      None
    else
      Some(
        agentCodeOpt match {
          case None => identifiers
          case Some(ac) => identifiers + ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, ac)
        }
      )
    end if
  end captureIdentifiersAndAgentCodeFrom

  private def captureIdentifiersFrom(enrolments: Enrolments): Set[ServiceIdentifier] =

    case class AgentEnrolment(
      legacyAgentEnrolmentType: LegacyAgentEnrolmentType,
      values: Seq[String]
    )

    enrolments.enrolments
      .map(enrolment =>
        LegacyAgentEnrolmentType
          .findByName(enrolment.key)
          .map(eType => AgentEnrolment(eType, enrolment.identifiers.map(_.value)))
      )
      .collect:
        // We only use the FIRST value; since all the enrolments we care about are single valued
        case Some(AgentEnrolment(enrolmentType, Seq(enrolmentIdentifierValue))) => ServiceIdentifier(enrolmentType, enrolmentIdentifierValue)

  end captureIdentifiersFrom

end AuthActions
