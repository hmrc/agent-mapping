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

import org.bson.types.ObjectId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping.databaseFormat
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory

class AgentReferenceMappingSpec
extends AnyWordSpecLike
with Matchers {

  implicit val crypto: Encrypter
    with Decrypter = SymmetricCryptoFactory.aesCrypto(secretKey = "GTfz3GZy0+gN0p/5wSqRBpWlbWVDMezXWtX+G9ENwCc=")

  "AgentReferenceMapping model" should {
    "read from JSON" when {
      "the database reads is used (encrypted)" in {
        val expectedModel: AgentReferenceMapping = AgentReferenceMapping(
          Some(new ObjectId("69038f00be7a033d7d28132f")),
          Arn("XARN1234567"),
          "ABC123"
        )
        val json: JsObject = Json.obj(
          "_id" -> Json.obj("$oid" -> "69038f00be7a033d7d28132f"),
          "arn" -> "XARN1234567",
          "identifier" -> "xBz9KLLVGclaDNLxWSY/YA=="
        )
        json.as[AgentReferenceMapping](databaseFormat) shouldBe expectedModel
      }
      "the database reads is used (encrypted) but ObjectId is invalid" in {
        val expectedModel: AgentReferenceMapping = AgentReferenceMapping(
          None,
          Arn("XARN1234567"),
          "ABC123"
        )
        val json: JsObject = Json.obj(
          "_id" -> Json.obj("$oid" -> "invalidObjectId"),
          "arn" -> "XARN1234567",
          "identifier" -> "xBz9KLLVGclaDNLxWSY/YA=="
        )
        json.as[AgentReferenceMapping](databaseFormat) shouldBe expectedModel
      }
      "the database reads is used (encrypted) but ObjectId is missing" in {
        val expectedModel: AgentReferenceMapping = AgentReferenceMapping(
          None,
          Arn("XARN1234567"),
          "ABC123"
        )
        val json: JsObject = Json.obj(
          "arn" -> "XARN1234567",
          "identifier" -> "xBz9KLLVGclaDNLxWSY/YA=="
        )
        json.as[AgentReferenceMapping](databaseFormat) shouldBe expectedModel
      }
    }

    "write to JSON" when {
      "the api writes is used (not timestamped)" in {
        val model: AgentReferenceMapping = AgentReferenceMapping(
          None,
          Arn("XARN1234567"),
          "ABC123"
        )
        val expectedJson: JsObject = Json.obj(
          "arn" -> "XARN1234567",
          "identifier" -> "ABC123"
        )
        Json.toJson(model)(AgentReferenceMapping.apiWrites()) shouldBe expectedJson
      }

      "the api writes is used (timestamped)" in {
        val model: AgentReferenceMapping = AgentReferenceMapping(
          Some(new ObjectId("69038f00be7a033d7d28132f")),
          Arn("XARN1234567"),
          "ABC123"
        )
        val expectedJson: JsObject = Json.obj(
          "arn" -> "XARN1234567",
          "identifier" -> "ABC123",
          "created" -> "2025-10-30"
        )
        Json.toJson(model)(AgentReferenceMapping.apiWrites()) shouldBe expectedJson
      }

      "the api writes is used with identifier key override (timestamped)" in {
        val model: AgentReferenceMapping = AgentReferenceMapping(
          Some(new ObjectId("69038f00be7a033d7d28132f")),
          Arn("XARN1234567"),
          "ABC123"
        )
        val expectedJson: JsObject = Json.obj(
          "arn" -> "XARN1234567",
          "agentCode" -> "ABC123",
          "created" -> "2025-10-30"
        )
        Json.toJson(model)(AgentReferenceMapping.apiWrites("agentCode")) shouldBe expectedJson
      }

      "the database writes is used (encrypted)" in {
        val model: AgentReferenceMapping = AgentReferenceMapping(
          None,
          Arn("XARN1234567"),
          "ABC123"
        )
        val expectedJson: JsObject = Json.obj(
          "arn" -> "XARN1234567",
          "identifier" -> "xBz9KLLVGclaDNLxWSY/YA=="
        )
        Json.toJson(model)(databaseFormat) shouldBe expectedJson
      }
    }
  }

}
