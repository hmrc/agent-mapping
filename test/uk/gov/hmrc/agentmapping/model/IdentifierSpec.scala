/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.agentmapping.model

import uk.gov.hmrc.play.test.UnitSpec

class IdentifierSpec extends UnitSpec {

  "Identifiers" should {
    "parse arguments into an Identifiers object" in {
      Identifiers.parse("foo") shouldBe Identifiers(Seq(Identifier("IRAgentReference", "foo")))
      Identifiers.parse("foo~bar") shouldBe Identifiers(Seq(Identifier("foo", "bar")))
      Identifiers.parse("foo~bar~foo~bar") shouldBe Identifiers(Seq(Identifier("foo", "bar"), Identifier("foo", "bar")))
      an[IllegalArgumentException] shouldBe thrownBy(
        Identifiers.parse("foo~bar~foo"))
      Identifiers.parse("foo~bar~goo~car~hoo~dar") shouldBe Identifiers(Seq(Identifier("foo", "bar"), Identifier("goo", "car"), Identifier("hoo", "dar")))
    }
  }
}
