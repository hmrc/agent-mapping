package uk.gov.hmrc.agentmapping.audit

import org.mockito.ArgumentCaptor
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.agentmapping.audit.AgentMappingEvent._
import uk.gov.hmrc.play.audit.model.DataEvent
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}


class AuditServiceSpec extends UnitSpec with MockitoSugar {

  "AuditService" should {
    "successfully create and send an audit event" in new AuditServiceSetup {

      await(testAuditService.auditEvent(KnownFactsCheck, "testing", Utr("4000000009"), Arn("GARN0000247"), None)(hc, request))

      verify(mockAuditConnector).sendEvent(eventCaptor.capture())(any(), any())

      val event: DataEvent = eventCaptor.getValue
      event.auditSource shouldBe "agent-mapping"
      event.auditType shouldBe "KnownFactsCheck"
      event.detail should have size 7
      event.detail.get("utr") shouldBe Some("4000000009")
      event.detail.get("agentReferenceNumber") shouldBe Some("GARN0000247")
      event.detail.contains("knownFactsMatched")
    }

    "successfully create and send an audit event with extra details" in new AuditServiceSetup {

      await(testAuditService.auditEvent(KnownFactsCheck, "testing", Utr("4000000009"), Arn("GARN0000247"), Some("Cred12345"), Seq("knownFactsMatched" -> true))(hc, request))

      verify(mockAuditConnector).sendEvent(eventCaptor.capture())(any(), any())

      val event: DataEvent = eventCaptor.getValue
      event.auditSource shouldBe "agent-mapping"
      event.auditType shouldBe "KnownFactsCheck"
      event.detail should have size 8
      event.detail.get("utr") shouldBe Some("4000000009")
      event.detail.get("agentReferenceNumber") shouldBe Some("GARN0000247")
      event.detail.contains("knownFactsMatched")
      event.detail.get("authProviderId") shouldBe Some("Cred12345")
      event.detail.get("knownFactsMatched") shouldBe Some("true")

    }

  }

}

class AuditServiceSetup extends MockitoSugar {
  val mockAuditConnector = mock[AuditConnector]
  val testAuditService = new AuditService(mockAuditConnector)

  val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])

 implicit val hc = HeaderCarrier(
    authorization = Some(Authorization("bearer token"))
  )

  implicit val request = FakeRequest("GET", "/path")
}

