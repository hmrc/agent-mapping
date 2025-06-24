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

package uk.gov.hmrc.agentmapping.repository

import org.apache.pekko.stream.Materializer
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Updates
import org.mongodb.scala.result.DeleteResult
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import test.uk.gov.hmrc.agentmapping.support.MetricTestSupport
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

class IRSAAGENTMappingRepositoryISpec
extends BaseRepositoryISpec[AgentReferenceMapping, IRSAAGENTMappingRepository]
with DefaultPlayMongoRepositorySupport[AgentReferenceMapping]
with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    super.beforeAll()
    givenCleanMetricRegistry()
    ()
  }
  override lazy val repository = new IRSAAGENTMappingRepository(mongoComponent)

}

class NewAgentCodeMappingRepositoryISpec
extends BaseRepositoryISpec[AgentReferenceMapping, NewAgentCodeMappingRepository]
with DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new NewAgentCodeMappingRepository(mongoComponent)

}

class HMCEVATAGNTMappingRepositoryISpec
extends BaseRepositoryISpec[AgentReferenceMapping, HMCEVATAGNTMappingRepository]
with DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new HMCEVATAGNTMappingRepository(mongoComponent)

}

class HMRCCHARAGENTMappingRepositoryISpec
extends BaseRepositoryISpec[AgentReferenceMapping, HMRCCHARAGENTMappingRepository]
with DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new HMRCCHARAGENTMappingRepository(mongoComponent)

}
class HMRCGTSAGNTMappingRepositoryISpec
extends BaseRepositoryISpec[AgentReferenceMapping, HMRCGTSAGNTMappingRepository]
with DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new HMRCGTSAGNTMappingRepository(mongoComponent)

}

class HMRCMGDAGNTMappingRepositoryISpec
extends BaseRepositoryISpec[AgentReferenceMapping, HMRCMGDAGNTMappingRepository]
with DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new HMRCMGDAGNTMappingRepository(mongoComponent)

}
class HMRCNOVRNAGNTMappingRepositoryISpec
extends BaseRepositoryISpec[AgentReferenceMapping, HMRCNOVRNAGNTMappingRepository]
with DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new HMRCNOVRNAGNTMappingRepository(mongoComponent)

}
class IRCTAGENTMappingRepositoryISpec
extends BaseRepositoryISpec[AgentReferenceMapping, IRCTAGENTMappingRepository]
with DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new IRCTAGENTMappingRepository(mongoComponent)

}
class IRPAYEAGENTMappingRepositoryISpec
extends BaseRepositoryISpec[AgentReferenceMapping, IRPAYEAGENTMappingRepository]
with DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new IRPAYEAGENTMappingRepository(mongoComponent)

}
class IRSDLTAGENTMappingRepositoryISpec
extends BaseRepositoryISpec[AgentReferenceMapping, IRSDLTAGENTMappingRepository]
with DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new IRSDLTAGENTMappingRepository(mongoComponent)
}

abstract class BaseRepositoryISpec[
  T <: ArnToIdentifierMapping,
  R <: MappingRepository: ClassTag
]
extends AnyWordSpecLike
with Matchers
with OptionValues
with ScalaFutures
with IntegrationPatience
with GuiceOneAppPerSuite
with MetricTestSupport {

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]
  implicit val crypto: Encrypter
    with Decrypter = SymmetricCryptoFactory.aesCrypto(secretKey = "GTfz3GZy0+gN0p/5wSqRBpWlbWVDMezXWtX+G9ENwCc=")
  def repository: MappingRepository

  override implicit lazy val app: Application = appBuilder.build()
  protected def appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      Map(
        "metrics.enabled" -> false,
        "migrate-repositories" -> "false",
        "termination.stride.enrolment" -> "caat"
      )
    )

  val arn1: Arn = Arn("ARN00001")
  val arn2: Arn = Arn("ARN00002")

  val utr1: Utr = Utr("4000000009")
  val utr2: Utr = Utr("7000000002")

  val reference1 = "Ref0001"
  val reference2 = "Ref0002"

  private val repoName = repository.getClass.getSimpleName

  s"$repoName" should {
    behave like checkMapping(arn1, Seq(reference1, reference2))
    behave like checkMapping(utr1, Seq(reference1, reference2))

    def checkMapping(
      businessId: TaxIdentifier,
      references: Seq[String]
    ): Unit = {
      val reference1 = references.head
      val reference2 = references.last

      s"create a mapping for {${businessId.getClass.getSimpleName}}" in {
        repository.store(businessId, reference1).futureValue

        val result = repository.findAll().futureValue

        result.size shouldBe 1
        result.head.businessId.value shouldBe businessId.value
        result.head.identifier shouldBe reference1
      }

      s"not allow duplicate mappings to be created for the same {${businessId.getClass.getSimpleName} and identifier" in {
        repository.store(businessId, reference1).futureValue

        val e = repository.store(businessId, reference1).failed.futureValue

        e shouldBe a[MongoWriteException]
        e.getMessage should include("E11000")
      }

      s"allow more than one identifier to be mapped to the same {${businessId.getClass.getSimpleName}" in {
        repository.store(businessId, reference1).futureValue
        repository.store(businessId, reference2).futureValue

        val result = repository.findAll().futureValue

        result.size shouldBe 2
        result.head.businessId.value shouldBe businessId.value
        result.head.identifier shouldBe reference1
        result(1).businessId.value shouldBe businessId.value
        result(1).identifier shouldBe reference2
      }
    }

    "find all mappings for Arn" in {
      repository.store(arn1, reference1).futureValue
      repository.store(arn1, reference2).futureValue
      repository.store(arn2, reference2).futureValue

      val result: Seq[ArnToIdentifierMapping] = repository.findBy(arn1).futureValue

      result.size shouldBe 2
    }

    "return an empty list when no match is found for Arn" in {
      repository.store(arn1, reference1).futureValue
      val result = repository.findBy(arn2).futureValue
      result.size shouldBe 0
    }

    "delete a matching records by Arn" in {
      repository.store(arn1, reference1).futureValue
      repository.store(arn2, reference2).futureValue

      val mappings: Seq[ArnToIdentifierMapping] = repository.findAll().futureValue
      mappings.size shouldBe 2

      val result: DeleteResult = repository.deleteByArn(arn1).futureValue
      result.getDeletedCount shouldBe 1L

      val mappingsAfterDelete: Seq[ArnToIdentifierMapping] = repository.findAll().futureValue
      mappingsAfterDelete.size shouldBe 1
    }

    "do not fail delete when no matching records exist for Arn" in {
      val mappings: Seq[ArnToIdentifierMapping] = repository.findAll().futureValue
      mappings.size shouldBe 0

      val result: DeleteResult = repository.deleteByArn(arn1).futureValue
      result.getDeletedCount shouldBe 0L
    }

    "find all mappings for Utr" in {
      repository.store(utr1, reference1).futureValue
      repository.store(utr1, reference2).futureValue
      repository.store(utr2, reference2).futureValue

      val result: Seq[ArnToIdentifierMapping] = repository.findBy(utr1).futureValue

      result.size shouldBe 2
    }

    "return an empty list when no match is found for Utr" in {
      repository.store(utr1, reference1).futureValue
      val result = repository.findBy(utr2).futureValue
      result.size shouldBe 0
    }

    "delete a matching records by Utr" in {
      repository.store(utr1, reference1).futureValue
      repository.store(utr2, reference2).futureValue

      val mappings: Seq[ArnToIdentifierMapping] = repository.findAll().futureValue
      mappings.size shouldBe 2

      val result: DeleteResult = repository.deleteByUtr(utr1).futureValue
      result.getDeletedCount shouldBe 1L

      val mappingsAfterDelete: Seq[ArnToIdentifierMapping] = repository.findAll().futureValue
      mappingsAfterDelete.size shouldBe 1
    }

    "do not fail delete when no matching records exist for Utr" in {
      val mappings: Seq[ArnToIdentifierMapping] = repository.findAll().futureValue
      mappings.size shouldBe 0

      val result: DeleteResult = repository.deleteByUtr(utr1).futureValue
      result.getDeletedCount shouldBe 0L
    }

    "update records with utr to arn" in {
      repository.store(utr1, reference1).futureValue

      val mappings: Seq[ArnToIdentifierMapping] = repository.findAll().futureValue
      mappings.size shouldBe 1

      repository.updateUtrToArn(utr1, arn1).futureValue

      val updatedMappings: Seq[ArnToIdentifierMapping] = repository.findAll().futureValue
      updatedMappings.size shouldBe 1

      updatedMappings.head.businessId shouldBe arn1
      updatedMappings.head.identifier shouldBe reference1

      repository.findBy(arn1).futureValue shouldBe List(updatedMappings.head)
      repository.findBy(utr1).futureValue shouldBe List.empty
    }
  }

  "countUnencrypted" should {

    "return the number of items missing the 'encrypted' field and correctly ignore items with the field" in {
      repository.collection.insertOne(
        AgentReferenceMapping(
          Arn("XARN1234567"),
          "ABC123",
          None,
          Some(true)
        )
      ).toFuture().futureValue
      repository.collection.insertOne(
        AgentReferenceMapping(
          Utr("1234567890"),
          "ABC123",
          Some(LocalDateTime.now()),
          Some(true)
        )
      ).toFuture().futureValue
      repository.collection.insertOne(
        AgentReferenceMapping(
          Arn("ZARN1234567"),
          "ABC123",
          None,
          Some(true)
        )
      ).toFuture().futureValue
      repository.collection.updateOne(
        Filters.equal("arn", "XARN1234567"),
        Updates.unset("encrypted")
      ).toFuture().futureValue
      repository.collection.updateOne(
        Filters.equal("utr", "1234567890"),
        Updates.unset("encrypted")
      ).toFuture().futureValue

      repository.countUnencrypted().futureValue shouldBe 2
    }
  }

  "encryptOldRecords" should {

    "iterate through all unencrypted items in the database and store them with encryption applied" in {
      repository.collection.insertOne(
        AgentReferenceMapping(
          Arn("XARN1234567"),
          "ABC123",
          None,
          Some(true)
        )
      ).toFuture().futureValue
      repository.collection.insertOne(
        AgentReferenceMapping(
          Utr("1234567890"),
          "ABC123",
          Some(LocalDateTime.now()),
          Some(true)
        )
      ).toFuture().futureValue
      repository.collection.insertOne(
        AgentReferenceMapping(
          Arn("XARN1234567"),
          "XYZ123",
          None,
          Some(true)
        )
      ).toFuture().futureValue
      repository.collection.updateOne(
        Filters.and(Filters.equal("arn", "XARN1234567"), Filters.equal("identifier", "ABC123")),
        Updates.unset("encrypted")
      ).toFuture().futureValue
      repository.collection.updateOne(
        Filters.equal("utr", "1234567890"),
        Updates.unset("encrypted")
      ).toFuture().futureValue
      repository.collection.updateOne(
        Filters.and(Filters.equal("arn", "XARN1234567"), Filters.equal("identifier", "XYZ123")),
        Updates.unset("encrypted")
      ).toFuture().futureValue

      val throttleRate = 2
      repository.encryptOldRecords(throttleRate)
      eventually(timeout(Span(5, Seconds)), interval(Span(100, Millis))) {
        repository.countUnencrypted().futureValue shouldBe 0
      }
    }
  }

}
