package uk.gov.hmrc.agentmapping.auth
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmapping.model.Identifier
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AuthActionsISpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val mockAuthConnector = mock[AuthConnector]
  val mockAuthActions = new AuthActions(mockAuthConnector)

  override def beforeEach(): Unit = reset(mockAuthConnector)

  "BasicAuth" should {
    def response = await(mockAuthActions.BasicAuth(basicAction).apply(fakeRequestAny))
    "return 200 if the user has a valid auth token" in {
      authStub[Unit](Future.successful(EmptyRetrieval))

      status(response) shouldBe 200
    }

    "return 401 if the user does not have a valid auth token" in {
      authStub[Unit](Future.failed(MissingBearerToken("")))

      status(response) shouldBe 401
    }
  }

  "AuthorisedWithEnrolments" should {
    def response = await(mockAuthActions.AuthorisedWithEnrolments(authorisedWithEnrolmentsAction).apply(fakeRequestAny))

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
    def response = await(mockAuthActions.AuthorisedAsAgent(authorisedAsAgentAction)(error).apply(fakeRequestAny))

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
    def response = await(mockAuthActions.AuthorisedWithAgentCode(authorisedWithAgentCodeAction)(error).apply(fakeRequestAny))

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

  private val basicAction: Request[AnyContent] => Future[Result] = { implicit request => Future.successful(Ok)}
  private val authorisedWithEnrolmentsAction: Request[AnyContent] => Boolean => Future[Result] = { implicit request => eligibility => Future.successful(Ok(eligibility.toString))}
  private val authorisedAsAgentAction: Request[AnyContent] => Arn => Future[Result] = { implicit request => arn => Future.successful(Ok(arn.value))}
  private val authorisedWithAgentCodeAction: Request[AnyContent] => Set[Identifier] => String => Future[Result] = { implicit request => identifier => provider => Future.successful(Ok)}

  private def authStub[A](returnValue: Future[A]) =
    when(mockAuthConnector.authorise(any[authorise.Predicate](), any[Retrieval[A]])(any(), any())).thenReturn(returnValue)

  private val fakeRequestAny: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  private val agentEnrolment = Set(
    Enrolment("HMRC-AS-AGENT", Seq(EnrolmentIdentifier("AgentReferenceNumber", "AARN001")), state = "",
      delegatedAuthRule = None))

  private val saEnrolment = Set(
    Enrolment("IR-SA-AGENT", Seq(EnrolmentIdentifier("sa", "00001")), state = "",
      delegatedAuthRule = None))
  
  private val error: PartialFunction[Throwable, Result] = (error)
}