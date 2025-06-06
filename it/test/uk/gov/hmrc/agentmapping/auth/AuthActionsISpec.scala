/*
 * Copyright 2024 HM Revenue & Customs
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

package test.uk.gov.hmrc.agentmapping.auth

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.mvc.ControllerComponents
import play.api.mvc.Results._
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.test.Helpers.status
import uk.gov.hmrc.agentmapping.model.Identifier
import test.uk.gov.hmrc.agentmapping.support.BaseISpec
import uk.gov.hmrc.agentmapping.auth.AuthActions
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AuthActionsISpec
extends BaseISpec
with MockitoSugar
with ScalaFutures {

  lazy val app: Application = appBuilder.build()

  val cc = app.injector.instanceOf[ControllerComponents]

  val mockAuthConnector = mock[AuthConnector]
  val mockAuthActions = new AuthActions(mockAuthConnector, cc)

  val newStrideId = "maintain_agent_relationships"
  val terminateStrideId = "caat"

  val newStrideEnrolment = Set(Enrolment(newStrideId))
  val terminateStrideEnrolment = Set(Enrolment(terminateStrideId))

  override def beforeEach(): Unit = reset(mockAuthConnector)

  "BasicAuth" should {
    def response = mockAuthActions.basicAuth(basicAction).apply(fakeRequestAny)
    "return 200 if the user has a valid auth token" in {
      authStub[Unit](Future.successful(EmptyRetrieval).map(_ => ()))

      status(response) shouldBe 200
    }

    "return 401 if the user does not have a valid auth token" in {
      authStub[Unit](Future.failed(MissingBearerToken("")))

      status(response) shouldBe 401
    }
  }

  "AuthorisedWithEnrolments" should {
    def response = mockAuthActions.authorisedWithEnrolments(authorisedWithEnrolmentsAction).apply(fakeRequestAny)

    "return 200 and true if the user has eligible enrolments" in {
      authStub[Enrolments](Future.successful(Enrolments(saEnrolment)))

      status(response) shouldBe 200
      contentAsString(response) shouldBe "true"
    }

    "return 200 and false if the user has ineligible enrolments" in {
      authStub[Enrolments](Future.successful(Enrolments(agentEnrolment)))

      status(response) shouldBe 200
      contentAsString(response) shouldBe "false"
    }

    "return 200 and false if the user has no enrolments1" in {
      authStub[Enrolments](Future.successful(Enrolments(Set.empty)))

      status(response) shouldBe 200
      contentAsString(response) shouldBe "false"
    }
  }

  "AuthorisedAsAgent" should {
    def response = mockAuthActions.authorisedAsSubscribedAgent(authorisedAsAgentAction).apply(fakeRequestAny)

    "return 200 and arn if the user has HMRC-AS-AGENT enrolment" in {
      authStub[Enrolments](Future.successful(Enrolments(agentEnrolment)))
      status(response) shouldBe 200
      contentAsString(response) shouldBe "AARN001"
    }

    "return 403 if the user does not have HMRC-AS-AGENT enrolment" in {
      authStub[Enrolments](Future.successful(Enrolments(saEnrolment)))
      status(response) shouldBe 403
    }
  }

  "AuthorisedWithAgentCode" should {
    def response = mockAuthActions.authorisedWithAgentCode(authorisedWithAgentCodeAction).apply(fakeRequestAny)

    "return 200 for users with an agent code and validenrolments" in {
      authStub[~[~[Option[Credentials], Option[String]], Enrolments]](
        Future.successful(
          new ~(new ~(Some(Credentials("providerId", "providerType")), Some("agentCode")), Enrolments(saEnrolment))
        )
      )

      status(response) shouldBe 200
    }

    "return 403 for users with an agent code and invalid enrolments" in {
      authStub[~[~[Option[Credentials], Option[String]], Enrolments]](
        Future.successful(
          new ~(new ~(Some(Credentials("providerId", "providerType")), Some("agentCode")), Enrolments(agentEnrolment))
        )
      )

      status(response) shouldBe 403
    }

    "return 403 for users with empty agent code and invalid enrolments" in {
      authStub[~[~[Option[Credentials], Option[String]], Enrolments]](
        Future.successful(new ~(new ~(Some(Credentials("providerId", "providerType")), None), Enrolments(Set.empty)))
      )

      status(response) shouldBe 403
    }
  }

  "onlyStride" should {

    "return 200 for successful stride authentication" in {
      authStub(onlyStride(terminateStrideEnrolment))

      val response: Future[Result] = mockAuthActions.onlyStride(terminateStrideId)(strideAction).apply(fakeRequestAny)

      status(response) shouldBe 200
    }

    "return 401 for unauthorised stride authentication" in {
      authStub(onlyStride(newStrideEnrolment))

      val response: Future[Result] = mockAuthActions.onlyStride(terminateStrideId)(strideAction).apply(fakeRequestAny)

      status(response) shouldBe 401
    }

    "return 403 for unsuccessful stride authentication" in {
      authStub(onlyStrideFail)

      val response: Future[Result] = mockAuthActions.onlyStride(terminateStrideId)(strideAction).apply(fakeRequestAny)

      status(response) shouldBe 403
    }
  }

  private val basicAction: Request[AnyContent] => Future[Result] = { request => Future.successful(Ok) }
  private val authorisedWithEnrolmentsAction: Request[AnyContent] => Boolean => Future[Result] = {
    request => eligibility => Future.successful(Ok(eligibility.toString))
  }
  private val authorisedAsAgentAction: Request[AnyContent] => Arn => Future[Result] = { request => arn =>
    Future.successful(Ok(arn.value))
  }
  private val authorisedWithAgentCodeAction: Request[AnyContent] => Set[Identifier] => String => Future[Result] = {
    request => identifier => provider => Future.successful(Ok)
  }

  val strideAction: Request[AnyContent] => Future[Result] = { request => Future successful Ok }

  private def authStub[A](returnValue: Future[A]) = when(mockAuthConnector.authorise(
    any[Predicate](),
    any[Retrieval[A]]
  )(any[HeaderCarrier], any[ExecutionContext]))
    .thenReturn(returnValue)

  private val fakeRequestAny: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  private val agentEnrolment = Set(
    Enrolment(
      "HMRC-AS-AGENT",
      Seq(EnrolmentIdentifier("AgentReferenceNumber", "AARN001")),
      state = "",
      delegatedAuthRule = None
    )
  )

  private val saEnrolment = Set(
    Enrolment(
      "IR-SA-AGENT",
      Seq(EnrolmentIdentifier("sa", "00001")),
      state = "",
      delegatedAuthRule = None
    )
  )

  val onlyStride: Set[Enrolment] => Future[Enrolments] = strideEnrolments => Future successful Enrolments(strideEnrolments)

  val onlyStrideFail: Future[Enrolments] = Future failed new UnsupportedAuthProvider

}
