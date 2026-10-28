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

package uk.gov.hmrc.agentmapping.repository

import org.mongodb.scala.Document
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.model.Filters
import org.mongodb.scala.result.DeleteResult
import org.scalatest.OptionValues
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.agentmapping.model.*
import uk.gov.hmrc.agentmapping.util.NoRequest
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.crypto.SymmetricCryptoFactory
import uk.gov.hmrc.mongo.MongoUtils.DuplicateKey
import uk.gov.hmrc.mongo.play.json.Codecs.JsonOps
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Deadline
import scala.reflect.ClassTag

/** Common test cases for all MappingRepository implementations.
  *
  * @tparam Repo
  *   the type of the repository under test
  */
trait MappingRepositoryBehaviours[Repo <: MappingRepository]
extends AnyWordSpecLike
with Matchers
with OptionValues
with ScalaFutures
with IntegrationPatience
with DefaultPlayMongoRepositorySupport[AgentReferenceMapping]:

  given ExecutionContext = ExecutionContext.global
  given crypto: (Encrypter & Decrypter) = SymmetricCryptoFactory.aesCrypto("GTfz3GZy0+gN0p/5wSqRBpWlbWVDMezXWtX+G9ENwCc=")

  override val repository: Repo

  val arn1: Arn = Arn("ARN00001")
  val arn2: Arn = Arn("ARN00002")

  val reference1 = "Ref0001"
  val reference2 = "Ref0002"
  val references: Seq[String] = Seq(reference1, reference2)

  def aMappingRepository(): Unit =
    val reference1 = references.head
    val reference2 = references.last

    "create a mapping for Arn" in:
      repository.store(arn1, reference1).futureValue

      val result = repository.findAll().futureValue

      result.size shouldBe 1
      result.head.arn.value shouldBe arn1.value
      result.head.identifier shouldBe reference1
    
    s"not allow duplicate mappings to be created for the same Arn and identifier" in:
      repository.store(arn1, reference1).futureValue

      val e = repository.store(arn1, reference1).failed.futureValue

      e shouldBe a[MongoWriteException]
      e.getMessage should include("E11000")
    
    s"allow more than one identifier to be mapped to the same Arn" in:
      repository.store(arn1, reference1).futureValue
      repository.store(arn1, reference2).futureValue

      val result = repository.findAll().futureValue

      result.size shouldBe 2
      result.head.arn.value shouldBe arn1.value
      result.head.identifier shouldBe reference1
      result(1).arn.value shouldBe arn1.value
      result(1).identifier shouldBe reference2
    
    "find all mappings for Arn" in:
      repository.store(arn1, reference1).futureValue
      repository.store(arn1, reference2).futureValue
      repository.store(arn2, reference2).futureValue

      val result: Seq[AgentReferenceMapping] = repository.findBy(arn1).futureValue

      result.size shouldBe 2
    
    "return an empty list when no match is found for Arn" in:
      repository.store(arn1, reference1).futureValue
      val result = repository.findBy(arn2).futureValue
      result.size shouldBe 0

    "find all identifiers for Arn" in:
      repository.store(arn1, reference1).futureValue
      repository.store(arn1, reference2).futureValue
      repository.store(arn2, reference2).futureValue

      val result: Seq[String] = repository.findIdentifiersBy(arn1).futureValue

      result should contain theSameElementsAs Seq(reference1, reference2)

    "find all Arns for identifier" in:
      repository.store(arn1, reference1).futureValue
      repository.store(arn1, reference2).futureValue
      repository.store(arn2, reference2).futureValue

      val result: Seq[Arn] = repository.findArnBy(reference2).futureValue

      result should contain theSameElementsAs Seq(arn1, arn2)

    "delete a matching records by Arn" in:
      repository.store(arn1, reference1).futureValue
      repository.store(arn2, reference2).futureValue

      val mappings: Seq[AgentReferenceMapping] = repository.findAll().futureValue
      mappings.size shouldBe 2

      val result: DeleteResult = repository.deleteByArn(arn1).futureValue
      result.getDeletedCount shouldBe 1L

      val mappingsAfterDelete: Seq[AgentReferenceMapping] = repository.findAll().futureValue
      mappingsAfterDelete.size shouldBe 1

    "do not fail delete when no matching records exist for Arn" in:
      val mappings: Seq[AgentReferenceMapping] = repository.findAll().futureValue
      mappings.size shouldBe 0

      val result: DeleteResult = repository.deleteByArn(arn1).futureValue
      result.getDeletedCount shouldBe 0L

    "migrating document schemas" should:
      "replace encrypted identifier with plaintext service identifier" in:
        val legacyArn = Arn("ARN00099")
        val legacyIdentifier = "Ref0009"

        repository.store(legacyArn, legacyIdentifier).futureValue

        repository.collection
          .updateOne(
            Document("arn" -> legacyArn.toBson),
            Document(
              "$set" -> Document("identifier" -> crypto.encrypt(PlainText(legacyIdentifier)).value),
              "$unset" -> Document("identifier" -> "")
            )
          )
          .headOption()

        repository.migrate(batchSize = 10, deadline = Deadline.now + 5.seconds)(using NoRequest).futureValue shouldBe 1

        val result = repository.findBy(legacyArn).futureValue
        result.head.identifier shouldBe legacyIdentifier

      "backfill a `created` field from the object ID" in:
        val legacyArn = Arn("ARN00098")
        val legacyIdentifier = "Ref0008"

        repository.store(legacyArn, legacyIdentifier).futureValue

        val rawDocument = repository.collection.find[Document](Filters.equal("arn", legacyArn.toBson)).headOption().futureValue.value
        val mongoId = rawDocument("_id").asObjectId().getValue
        val mongoTimestamp = Instant.ofEpochSecond(mongoId.getTimestamp)

        repository.collection
          .updateOne(
            Document("arn" -> legacyArn.toBson),
            Document("$unset" -> Document("created" -> ""))
          )
          .headOption()

        repository.migrate(batchSize = 10, deadline = Deadline.now + 5.seconds)(using NoRequest).futureValue shouldBe 1

        val result = repository.findBy(legacyArn).futureValue
        result.head.created shouldBe mongoTimestamp

      "continue to prevent duplicate ARN/identifier pairs if added before migration completes" in:
        val legacyArn = Arn("ARN00099")
        val legacyIdentifier = "Ref0009"

        repository.store(legacyArn, legacyIdentifier).futureValue

        repository.collection
          .updateOne(
            Document("arn" -> legacyArn.toBson),
            Document(
              "$set" -> Document("identifier" -> crypto.encrypt(PlainText(legacyIdentifier)).value),
              "$unset" -> Document("identifier" -> "")
            )
          )
          .headOption()

        repository.store(legacyArn, legacyIdentifier).failed.futureValue match
          case DuplicateKey(_) => succeed
          case _ => fail("Expected a duplicate key error")
