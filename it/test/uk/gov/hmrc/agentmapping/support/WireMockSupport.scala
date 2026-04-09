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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Suite
import uk.gov.hmrc.agentmapping.support.Port
import uk.gov.hmrc.agentmapping.support.WireMockBaseUrl

import java.net.URL
import java.nio.file.Paths

trait WireMockSupport
extends BeforeAndAfterAll
with BeforeAndAfterEach:
  me: Suite =>

  def commonStubs(): Unit

  val wireMockPort: Int = WireMockSupport.wireMockPort
  val wireMockHost = "localhost"
  val wireMockBaseUrlAsString = s"http://$wireMockHost:$wireMockPort"
  val wireMockBaseUrl: URL = Paths.get(wireMockBaseUrlAsString).toUri.toURL
  protected implicit val implicitWireMockBaseUrl: WireMockBaseUrl = WireMockBaseUrl(wireMockBaseUrl)

  protected def basicWireMockConfig(): WireMockConfiguration = wireMockConfig()

  private val wireMockServer = new WireMockServer(basicWireMockConfig().port(wireMockPort))

  override protected def beforeAll(): Unit =
    super.beforeAll()
    configureFor(wireMockHost, wireMockPort)
    wireMockServer.start()
  end beforeAll

  override protected def afterAll(): Unit =
    wireMockServer.stop()
    super.afterAll()
  end afterAll

  override protected def beforeEach(): Unit =
    super.beforeEach()
    reset()
  end beforeEach

  protected def stopWireMockServer(): Unit = wireMockServer.stop()

  protected def startWireMockServer(): Unit = wireMockServer.start()

end WireMockSupport

object WireMockSupport:
  // We have to make the wireMockPort constant per-JVM instead of constant
  // per-WireMockSupport-instance because config values containing it are
  // cached in the GGConfig object
  private lazy val wireMockPort = Port.randomAvailable

end WireMockSupport
