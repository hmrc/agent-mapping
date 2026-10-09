/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentmapping.support

import com.google.inject.AbstractModule
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSResponse
import play.api.libs.ws.*
import uk.gov.hmrc.agentmapping.support.BaseISpec
import uk.gov.hmrc.agentmapping.module.DuplicateArnScanModule

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

abstract class ServerBaseISpec
extends BaseISpec
with GuiceOneServerPerSuite
with ScalaFutures:

  override implicit lazy val app: Application = appBuilder.build()

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder
      .disable[DuplicateArnScanModule]
      .configure(
        Map(
          "microservice.services.auth.port" -> wireMockPort.toString,
          "microservice.services.agent-subscription.port" -> wireMockPort.toString,
          "microservice.services.agent-subscription.host" -> wireMockHost,
          "auditing.consumer.baseUri.host" -> wireMockHost,
          "auditing.consumer.baseUri.port" -> wireMockPort.toString,
          "application.router" -> "testOnlyDoNotUseInAppConf.Routes",
          "migrate-repositories" -> "false",
          "termination.stride.enrolment" -> "caat"
        )
      )
      .overrides(new TestGuiceModule)
  end appBuilder

  protected class TestGuiceModule
  extends AbstractModule:

    override def configure(): Unit = {}

  end TestGuiceModule

  val url = s"http://localhost:$port/agent-mapping"
  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def callPost(
    path: String,
    body: JsValue
  ): WSResponse =
    wsClient
      .url(s"$url$path")
      .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
      .post(body)
      .futureValue

  def callGet(path: String): WSResponse =
    wsClient
      .url(s"$url$path")
      .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
      .get()
      .futureValue

  def callDelete(path: String): WSResponse =
    wsClient
      .url(s"$url$path")
      .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
      .delete()
      .futureValue

  def callPut(
    path: String,
    body: Option[String]
  ): WSResponse =

    if body.isDefined then
      wsClient
        .url(s"$url$path")
        .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
        .put(body.get)
        .futureValue
    else
      wsClient
        .url(s"$url$path")
        .withHttpHeaders("Content-Type" -> "application/json", "Authorization" -> "Bearer XYZ")
        .execute("PUT")
        .futureValue
    end if

  end callPut

  def basicAuth(string: String): String = Base64.getEncoder.encodeToString(string.getBytes(UTF_8))

end ServerBaseISpec
