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

package test.uk.gov.hmrc.agentmapping.support

import org.apache.pekko.stream.Materializer
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Application
import play.api.i18n.Lang
import play.api.i18n.Messages
import play.api.i18n.MessagesApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.twirl.api.HtmlFormat
import test.uk.gov.hmrc.agentmapping.stubs.DataStreamStub

abstract class BaseISpec
extends AnyWordSpecLike
with Matchers
with OptionValues
with WireMockSupport
with DataStreamStub
with ScalaFutures {

  def app: Application

  protected def appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port" -> wireMockPort,
      "microservice.services.enrolment-store-proxy.port" -> wireMockPort,
      "microservice.services.agent-subscription.port" -> wireMockPort,
      "metrics.enabled" -> false,
      "auditing.enabled" -> false,
      "clientCount.maxRecords" -> 40,
      "clientCount.batchSize" -> 7,
      "mongodb.uri" -> "mongodb://localhost:27017/test-agent-mapping",
      "auditing.consumer.baseUri.port" -> wireMockPort,
      "migrate-repositories" -> "false",
      "termination.stride.enrolment" -> "caat"
    )

  override def commonStubs(): Unit = {
    givenAuditConnector
    ()
  }

  protected implicit val materializer: Materializer = app.materializer

  private val messagesApi = app.injector.instanceOf[MessagesApi]
  private implicit val messages: Messages = messagesApi.preferred(Seq.empty[Lang])

  protected def htmlEscapedMessage(key: String): String = HtmlFormat.escape(Messages(key)).toString

}
