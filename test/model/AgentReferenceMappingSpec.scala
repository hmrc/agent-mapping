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
import org.mongodb.scala.bson.BsonDateTime
import org.mongodb.scala.bson.BsonObjectId
import org.mongodb.scala.bson.Document
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping.databaseFormat
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory
import uk.gov.hmrc.mongo.play.json.Codecs.JsonOps
import uk.gov.hmrc.mongo.play.json.Codecs.*

import java.time.Instant

class AgentReferenceMappingSpec
extends AnyWordSpecLike
with Matchers:

  implicit val crypto: Encrypter & Decrypter = SymmetricCryptoFactory.aesCrypto(secretKey = "GTfz3GZy0+gN0p/5wSqRBpWlbWVDMezXWtX+G9ENwCc=")

  "AgentReferenceMapping model" should:
    "read from JSON" when:
      "the database reads is used (encrypted)" in:
        val expectedModel: AgentReferenceMapping = AgentReferenceMapping(
          created = Instant.parse("2025-10-30T00:00:00Z"),
          Arn("XARN1234567"),
          "ABC123"
        )
        val bson = Document(
          "_id" -> BsonObjectId("69038f00be7a033d7d28132f"),
          "arn" -> "XARN1234567",
          "identifier" -> "ABC123",
          "created" -> BsonDateTime(1761782400000L)
        )
        bson.fromBson[AgentReferenceMapping](using databaseFormat) shouldBe expectedModel

      "fallback to the ObjectID when the created field is undefined" in:
        val objectId = new ObjectId()
        val expectedModel: AgentReferenceMapping = AgentReferenceMapping(
          created = Instant.ofEpochSecond(objectId.getTimestamp),
          Arn("XARN1234567"),
          "ABC123"
        )
        val bson = Document(
          "_id" -> objectId,
          "arn" -> "XARN1234567",
          "identifier" -> "ABC123"
        )
        bson.fromBson[AgentReferenceMapping](using databaseFormat) shouldBe expectedModel

      "fallback to the encrypted identifier when service identifier is missing" in:
        val expectedModel: AgentReferenceMapping = AgentReferenceMapping(
          created = Instant.parse("2025-10-30T00:00:00Z"),
          Arn("XARN1234567"),
          "ABC123"
        )
        val bson = Document(
          "_id" -> new ObjectId("69038f00be7a033d7d28132f"),
          "arn" -> "XARN1234567",
          "identifier" -> "xBz9KLLVGclaDNLxWSY/YA==",
          "created" -> BsonDateTime(1761782400000L)
        )
        bson.fromBson[AgentReferenceMapping](using databaseFormat) shouldBe expectedModel

    "write to JSON" when:
      "the api writes is used (not timestamped)" in:
        val model: AgentReferenceMapping = AgentReferenceMapping(
          created = Instant.parse("2025-10-30T00:00:00Z"),
          Arn("XARN1234567"),
          "ABC123"
        )
        val expectedJson: JsObject = Json.obj(
          "arn" -> "XARN1234567",
          "identifier" -> "ABC123",
          "created" -> "2025-10-30"
        )
        Json.toJson(model)(using AgentReferenceMapping.apiWrites()) shouldBe expectedJson

      "the api writes is used (timestamped)" in:
        val model: AgentReferenceMapping = AgentReferenceMapping(
          created = Instant.parse("2025-10-30T00:00:00Z"),
          Arn("XARN1234567"),
          "ABC123"
        )
        val expectedJson: JsObject = Json.obj(
          "arn" -> "XARN1234567",
          "identifier" -> "ABC123",
          "created" -> "2025-10-30"
        )
        Json.toJson(model)(using AgentReferenceMapping.apiWrites()) shouldBe expectedJson

      "the api writes is used with identifier key override (timestamped)" in:
        val model: AgentReferenceMapping = AgentReferenceMapping(
          created = Instant.parse("2025-10-30T00:00:00Z"),
          Arn("XARN1234567"),
          "ABC123"
        )
        val expectedJson: JsObject = Json.obj(
          "arn" -> "XARN1234567",
          "agentCode" -> "ABC123",
          "created" -> "2025-10-30"
        )
        Json.toJson(model)(using AgentReferenceMapping.apiWrites("agentCode")) shouldBe expectedJson

      "the database writes is used" in:
        val model: AgentReferenceMapping = AgentReferenceMapping(
          created = Instant.parse("2025-10-30T00:00:00Z"),
          Arn("XARN1234567"),
          "ABC123"
        )
        val expectedBson = Document(
          "arn" -> "XARN1234567",
          "identifier" -> "xBz9KLLVGclaDNLxWSY/YA==",
          "identifier" -> "ABC123",
          "automapped" -> false,
          "created" -> BsonDateTime(1761782400000L)
        ).toBsonDocument()
        given Format[AgentReferenceMapping] = databaseFormat(using crypto)
        model.toBson shouldBe expectedBson

end AgentReferenceMappingSpec
