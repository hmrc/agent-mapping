/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentmapping.config

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Environment
import play.api.Configuration
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolmentType
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate

class AppConfigSpec
extends AnyWordSpecLike
with Matchers:

  private val applicationConfiguration = Configuration.load(Environment.simple())

  "AppConfig" should:

    "load application.conf successfully" in:
      noException should be thrownBy:
        new AppConfig(new ServicesConfig(applicationConfiguration), applicationConfiguration)

    "return mandation dates for configured enrolment types" in:
      val configuration = Configuration("mandationDates" -> Map(
        "IR-SA-AGENT" -> "2026-01-01",
        "HMCE-VAT-AGNT" -> "2026-02-01"
      )).withFallback(applicationConfiguration)
      val appConfig = new AppConfig(new ServicesConfig(configuration), configuration)

      appConfig.mandationDateFor(LegacyAgentEnrolmentType.IRAgentReference) shouldBe Some(LocalDate.parse("2026-01-01"))
      appConfig.mandationDateFor(LegacyAgentEnrolmentType.AgentRefNo) shouldBe Some(LocalDate.parse("2026-02-01"))
      appConfig.mandationDateFor(LegacyAgentEnrolmentType.AgentCode) shouldBe None
