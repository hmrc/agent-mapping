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

import com.google.inject.AbstractModule
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentmapping.model.MappingDetailsRepositoryRecord

abstract class ServerBaseISpec extends BaseISpec with GuiceOneServerPerSuite with ScalaFutures {

  override implicit lazy val app: Application = appBuilder.build()

  override protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
//      .disable[PlayModule]
      .configure(
        Map(
          "microservice.services.auth.port"               -> wireMockPort,
          "microservice.services.agent-subscription.port" -> wireMockPort,
          "microservice.services.agent-subscription.host" -> wireMockHost,
          "auditing.consumer.baseUri.host"                -> wireMockHost,
          "auditing.consumer.baseUri.port"                -> wireMockPort,
          "application.router"                            -> "testOnlyDoNotUseInAppConf.Routes",
          "migrate-repositories"                          -> "false",
          "termination.stride.enrolment"                  -> "caat"
        )
      )
      .overrides(new TestGuiceModule)

  protected class TestGuiceModule extends AbstractModule {
    override def configure(): Unit = {}
  }

  def matchRecordIgnoringDateTime(
    mappingDisplayRecord: MappingDetailsRepositoryRecord
  ): Matcher[MappingDetailsRepositoryRecord] =
    new Matcher[MappingDetailsRepositoryRecord] {
      override def apply(left: MappingDetailsRepositoryRecord): MatchResult =
        if (
          mappingDisplayRecord.arn == left.arn &&
          mappingDisplayRecord.mappingDetails.map(m => (m.ggTag, m.count)) == left.mappingDetails
            .map(m => (m.ggTag, m.count))
        )
          MatchResult(matches = true, "", "")
        else throw new RuntimeException("matchRecord error")

    }

}
