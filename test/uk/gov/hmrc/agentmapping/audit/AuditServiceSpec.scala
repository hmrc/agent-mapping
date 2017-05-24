/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentmapping.audit

import java.net.URL

import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import org.mockito.Mockito.verify
import org.mockito.Matchers._
import org.mockito.ArgumentMatchers.any
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmapping.WSHttp
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{AuditEvent, DataEvent}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.{Authorization, RequestId, SessionId}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.agentmapping.audit.AgentMappingEvent.KnownFactsCheck
import uk.gov.hmrc.agentmapping.connector.{AuthConnector, AuthDetails}
import uk.gov.hmrc.agentmapping.support.WireMockSupport

import scala.concurrent.{ExecutionContext, Future}

class AuditServiceSpec extends UnitSpec with MockitoSugar with Eventually {
  "auditEvent" should {
    "send an event with the correct fields" in {
      val mockConnector = mock[AuditConnector]
      val mockAuthConnector = mock[AuthConnector]
      val service = new AuditService(mockConnector, mockAuthConnector)

      when(mockAuthConnector.currentAuthDetails()(any(),any())).thenReturn(Future.successful(Some(AuthDetails(Some("testCredId")))))

      val hc = HeaderCarrier(
        authorization = Some(Authorization("dummy bearer token")),
        sessionId = Some(SessionId("dummy session id")),
        requestId = Some(RequestId("dummy request id"))
      )

      await(service.auditEvent(
        KnownFactsCheck,
        "transaction name",
        Utr("4000000009"), Arn("GARN0000247"),
        Seq("extra1" -> "first extra detail", "extra2" -> "second extra detail")
      )(
        hc,
        FakeRequest("GET", "/path"))
      )

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[AuditEvent])
        verify(mockConnector).sendEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
        captor.getValue shouldBe an[DataEvent]
        val sentEvent = captor.getValue.asInstanceOf[DataEvent]

        sentEvent.auditType shouldBe "KnownFactsCheck"
        sentEvent.detail("utr") shouldBe "4000000009"
        sentEvent.detail("agentReferenceNumber") shouldBe "GARN0000247"
        sentEvent.detail("extra1") shouldBe "first extra detail"
        sentEvent.detail("extra2") shouldBe "second extra detail"

        sentEvent.tags.contains("Authorization") shouldBe false
        sentEvent.detail("Authorization") shouldBe "dummy bearer token"

        sentEvent.tags("transactionName") shouldBe "transaction name"
        sentEvent.tags("path") shouldBe "/path"
        sentEvent.tags("X-Session-ID") shouldBe "dummy session id"
        sentEvent.tags("X-Request-ID") shouldBe "dummy request id"
      }
    }
  }

}
