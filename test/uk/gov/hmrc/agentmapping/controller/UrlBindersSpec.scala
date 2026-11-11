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

package uk.gov.hmrc.agentmapping.controller

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.mvc.PathBindable
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolmentType
import uk.gov.hmrc.agentmapping.controller.UrlBinders.given

class UrlBindersSpec
extends AnyWordSpecLike
with Matchers:

  private val enrolmentTypeBinder = summon[PathBindable[LegacyAgentEnrolmentType]]

  "UrlBinders.arnBinder" should:
    "bind a valid ARN" in:
      UrlBinders.arnBinder.bind("arn", "JARN9876668") shouldBe Right(Arn("JARN9876668"))

    "reject an invalid ARN" in:
      UrlBinders.arnBinder.bind("arn", "invalid") shouldBe Left(raw""""invalid" is not a valid ARN""")

    "unbind an ARN to its value" in:
      UrlBinders.arnBinder.unbind("arn", Arn("JARN9876668")) shouldBe "JARN9876668"

  "UrlBinders LegacyAgentEnrolmentType binder" should:
    "bind a database key" in:
      enrolmentTypeBinder.bind("enrolmentType", "sa") shouldBe Right(LegacyAgentEnrolmentType.IRAgentReference)

    "bind an enrolment name" in:
      enrolmentTypeBinder.bind("enrolmentType", "IR-SA-AGENT") shouldBe Right(LegacyAgentEnrolmentType.IRAgentReference)

    "reject an unknown enrolment type" in:
      enrolmentTypeBinder.bind("enrolmentType", "unknown") shouldBe Left(
        "Cannot parse parameter 'enrolmentType' with value 'unknown' as LegacyAgentEnrolmentType"
      )

    "unbind an enrolment type to its database key" in:
      enrolmentTypeBinder.unbind("enrolmentType", LegacyAgentEnrolmentType.IRAgentReference) shouldBe "sa"
