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

package model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping.databaseFormat
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory

import java.time.LocalDateTime

class AgentReferenceMappingSpec
extends AnyWordSpecLike
with Matchers {

  implicit val crypto: Encrypter
    with Decrypter = SymmetricCryptoFactory.aesCrypto(secretKey = "GTfz3GZy0+gN0p/5wSqRBpWlbWVDMezXWtX+G9ENwCc=")

  "AgentReferenceMapping model" should {

    "read from JSON" when {

      "the default reads is used (unencrypted)" when {

        "an ARN is the business identifier - the date and encryption status should not be read" in {
          val expectedModel: AgentReferenceMapping = AgentReferenceMapping(
            Arn("XARN1234567"),
            "ABC123",
            None,
            None
          )
          val json = Json.obj(
            "arn" -> "XARN1234567",
            "identifier" -> "ABC123",
            "preCreatedDate" -> "2020-01-01T00:00:00.000",
            "encrypted" -> false
          )

          json.as[AgentReferenceMapping] shouldBe expectedModel
        }

        "a UTR is the business identifier - the encryption status should not be read" in {
          val expectedModel: AgentReferenceMapping = AgentReferenceMapping(
            Utr("1234567890"),
            "ABC123",
            Some(LocalDateTime.parse("2020-01-01T00:00:00.000")),
            None
          )
          val json = Json.obj(
            "utr" -> "1234567890",
            "identifier" -> "ABC123",
            "preCreatedDate" -> "2020-01-01T00:00:00.000",
            "encrypted" -> false
          )

          json.as[AgentReferenceMapping] shouldBe expectedModel
        }
      }

      "the database reads is used (encrypted)" when {

        "an ARN is the business identifier - the date should not be read" in {
          val expectedModel: AgentReferenceMapping = AgentReferenceMapping(
            Arn("XARN1234567"),
            "ABC123",
            None,
            Some(true)
          )
          val json: JsObject = Json.obj(
            "arn" -> "XARN1234567",
            "identifier" -> "xBz9KLLVGclaDNLxWSY/YA==",
            "preCreatedDate" -> "2020-01-01T00:00:00.000",
            "encrypted" -> true
          )
          json.as[AgentReferenceMapping](databaseFormat) shouldBe expectedModel
        }

        "a UTR is the business identifier" in {
          val expectedModel: AgentReferenceMapping = AgentReferenceMapping(
            Utr("1234567890"),
            "ABC123",
            Some(LocalDateTime.parse("2020-01-01T00:00:00.000")),
            Some(true)
          )
          val json: JsObject = Json.obj(
            "utr" -> "1234567890",
            "identifier" -> "xBz9KLLVGclaDNLxWSY/YA==",
            "preCreatedDate" -> "2020-01-01T00:00:00.000",
            "encrypted" -> true
          )
          json.as[AgentReferenceMapping](databaseFormat) shouldBe expectedModel
        }
      }
    }

    "write to JSON" when {

      "the default writes is used (unencrypted)" should {

        "an ARN is the business identifier - the date and encryption status should not be written" in {
          val model: AgentReferenceMapping = AgentReferenceMapping(
            Arn("XARN1234567"),
            "ABC123",
            Some(LocalDateTime.parse("2020-01-01T00:00:00.000")),
            Some(true)
          )
          val expectedJson: JsObject = Json.obj(
            "arn" -> "XARN1234567",
            "identifier" -> "ABC123"
          )
          Json.toJson(model) shouldBe expectedJson
        }

        "a UTR is the business identifier - the encryption status should not be written" in {
          val model: AgentReferenceMapping = AgentReferenceMapping(
            Utr("1234567890"),
            "ABC123",
            Some(LocalDateTime.parse("2020-01-01T00:00:00.000")),
            Some(true)
          )
          val expectedJson: JsObject = Json.obj(
            "utr" -> "1234567890",
            "identifier" -> "ABC123",
            "preCreatedDate" -> Json.obj("$date" -> Json.obj("$numberLong" -> "1577836800000"))
          )
          Json.toJson(model) shouldBe expectedJson
        }
      }

      "the database writes is used (encrypted)" should {

        "an ARN is the business identifier - the date should not be written" in {
          val model: AgentReferenceMapping = AgentReferenceMapping(
            Arn("XARN1234567"),
            "ABC123",
            Some(LocalDateTime.parse("2020-01-01T00:00:00.000")),
            Some(true)
          )
          val expectedJson: JsObject = Json.obj(
            "arn" -> "XARN1234567",
            "identifier" -> "xBz9KLLVGclaDNLxWSY/YA==",
            "encrypted" -> true
          )
          Json.toJson(model)(databaseFormat) shouldBe expectedJson
        }

        "a UTR is the business identifier" in {
          val model: AgentReferenceMapping = AgentReferenceMapping(
            Utr("1234567890"),
            "ABC123",
            Some(LocalDateTime.parse("2020-01-01T00:00:00.000")),
            Some(true)
          )
          val expectedJson: JsObject = Json.obj(
            "utr" -> "1234567890",
            "identifier" -> "xBz9KLLVGclaDNLxWSY/YA==",
            "preCreatedDate" -> Json.obj("$date" -> Json.obj("$numberLong" -> "1577836800000")),
            "encrypted" -> true
          )
          Json.toJson(model)(databaseFormat) shouldBe expectedJson
        }
      }
    }
  }

}
