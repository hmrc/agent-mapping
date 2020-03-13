package uk.gov.hmrc.agentmapping.auth
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.mvc.Results._
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmapping.model.Identifier
import uk.gov.hmrc.agentmapping.support.BaseISpec
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AuthActionsISpec(implicit val ec: ExecutionContext) extends BaseISpec with MockitoSugar  {

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
    def response = await(mockAuthActions.basicAuth(basicAction).apply(fakeRequestAny))
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
    def response = await(mockAuthActions.authorisedWithEnrolments(authorisedWithEnrolmentsAction).apply(fakeRequestAny))

    "return 200 and true if the user has eligible enrolments" in {
      authStub[Enrolments](Future.successful(Enrolments(saEnrolment)))

      status(response) shouldBe 200
      bodyOf(response) shouldBe "true"
    }

    "return 200 and false if the user has ineligible enrolments" in {
      authStub[Enrolments](Future.successful(Enrolments(agentEnrolment)))

      status(response) shouldBe 200
      bodyOf(response) shouldBe "false"
    }

    "return 200 and false if the user has no enrolments1" in {
      authStub[Enrolments](Future.successful(Enrolments(Set.empty)))

      status(response) shouldBe 200
      bodyOf(response) shouldBe "false"
    }
  }

  "AuthorisedAsAgent" should {
    def response = await(mockAuthActions.authorisedAsSubscribedAgent(authorisedAsAgentAction).apply(fakeRequestAny))

    "return 200 and arn if the user has HMRC-AS-AGENT enrolment" in {
      authStub[Enrolments](Future.successful(Enrolments(agentEnrolment)))
      status(response) shouldBe 200
      bodyOf(response) shouldBe "AARN001"
    }

    "return 403 if the user does not have HMRC-AS-AGENT enrolment" in {
      authStub[Enrolments](Future.successful(Enrolments(saEnrolment)))
      status(response) shouldBe 403
    }
  }

  "AuthorisedWithAgentCode" should {
    def response = await(mockAuthActions.authorisedWithAgentCode(authorisedWithAgentCodeAction).apply(fakeRequestAny))

    "return 200 for users with an agent code and validenrolments" in {
      authStub[~[~[Credentials, Option[String]], Enrolments]](Future.successful(
        new ~(new ~(Credentials("providerId", "providerType"), Some("agentCode")), Enrolments(saEnrolment))))

      status(response) shouldBe 200
    }

    "return 403 for users with an agent code and invalid enrolments" in {
      authStub[~[~[Credentials, Option[String]], Enrolments]](Future.successful(
        new ~(new ~(Credentials("providerId", "providerType"), Some("agentCode")), Enrolments(agentEnrolment))))

      status(response) shouldBe 403
    }

    "return 403 for users with empty agent code and invalid enrolments" in {
      authStub[~[~[Credentials, Option[String]], Enrolments]](Future.successful(
        new ~(new ~(Credentials("providerId", "providerType"), None), Enrolments(Set.empty))))

      status(response) shouldBe 403
    }
  }

  "onlyStride" should {
    implicit val hc = new HeaderCarrier

    "return 200 for successful stride authentication" in {
      authStub(onlyStride(terminateStrideEnrolment))

      val response: Result = await(mockAuthActions.onlyStride(terminateStrideId)(strideAction).apply(fakeRequestAny))

      status(response) shouldBe 200
    }

    "return 401 for unauthorised stride authentication" in {
      authStub(onlyStride(newStrideEnrolment))

      val response: Result = await(mockAuthActions.onlyStride(terminateStrideId)(strideAction).apply(fakeRequestAny))

      status(response) shouldBe 401
    }

    "return 403 for unsuccessful stride authentication" in {
      authStub(onlyStrideFail)

      val response: Result = await(mockAuthActions.onlyStride(terminateStrideId)(strideAction).apply(fakeRequestAny))

      status(response) shouldBe 403
    }
  }

  private val basicAction: Request[AnyContent] => Future[Result] = { implicit request => Future.successful(Ok)}
  private val authorisedWithEnrolmentsAction: Request[AnyContent] => Boolean => Future[Result] = { implicit request => eligibility => Future.successful(Ok(eligibility.toString))}
  private val authorisedAsAgentAction: Request[AnyContent] => Arn => Future[Result] = { implicit request => arn => Future.successful(Ok(arn.value))}
  private val authorisedWithAgentCodeAction: Request[AnyContent] => Set[Identifier] => String => Future[Result] = { implicit request => identifier => provider => Future.successful(Ok)}

  val strideAction: Request[AnyContent] => Credentials => Future[Result] = { implicit request => _ => Future successful Ok }

  private def authStub[A](returnValue: Future[A]) =
    when(mockAuthConnector.authorise(any[Predicate](), any[Retrieval[A]])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(returnValue)

  private val fakeRequestAny: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  private val agentEnrolment = Set(
    Enrolment("HMRC-AS-AGENT", Seq(EnrolmentIdentifier("AgentReferenceNumber", "AARN001")), state = "",
      delegatedAuthRule = None))

  private val saEnrolment = Set(
    Enrolment("IR-SA-AGENT", Seq(EnrolmentIdentifier("sa", "00001")), state = "",
      delegatedAuthRule = None))

  val onlyStride: Set[Enrolment] => Future[Enrolments] =
    strideEnrolments => Future successful Enrolments(strideEnrolments)

  val onlyStrideFail: Future[Enrolments] =
    Future failed new UnsupportedAuthProvider


}