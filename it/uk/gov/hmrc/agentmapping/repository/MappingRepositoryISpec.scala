package uk.gov.hmrc.agentmapping.repository

import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.result.DeleteResult
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

class IRSAAGENTMappingRepositoryISpec extends BaseRepositoryISpec[AgentReferenceMapping, IRSAAGENTMappingRepository] with
  DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new IRSAAGENTMappingRepository(mongoComponent)
}

class NewAgentCodeMappingRepositoryISpec
    extends BaseRepositoryISpec[AgentReferenceMapping, NewAgentCodeMappingRepository] with
      DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new NewAgentCodeMappingRepository(mongoComponent)
}

class HMCEVATAGNTMappingRepositoryISpec extends BaseRepositoryISpec[AgentReferenceMapping, HMCEVATAGNTMappingRepository] with
  DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new HMCEVATAGNTMappingRepository(mongoComponent)
}

class HMRCCHARAGENTMappingRepositoryISpec
    extends BaseRepositoryISpec[AgentReferenceMapping, HMRCCHARAGENTMappingRepository] with
      DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new HMRCCHARAGENTMappingRepository(mongoComponent)
}
class HMRCGTSAGNTMappingRepositoryISpec extends BaseRepositoryISpec[AgentReferenceMapping, HMRCGTSAGNTMappingRepository] with
  DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new HMRCGTSAGNTMappingRepository(mongoComponent)
}

class HMRCMGDAGNTMappingRepositoryISpec extends BaseRepositoryISpec[AgentReferenceMapping, HMRCMGDAGNTMappingRepository] with
  DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new HMRCMGDAGNTMappingRepository(mongoComponent)
}
class HMRCNOVRNAGNTMappingRepositoryISpec
    extends BaseRepositoryISpec[AgentReferenceMapping, HMRCNOVRNAGNTMappingRepository] with
      DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new HMRCNOVRNAGNTMappingRepository(mongoComponent)
}
class IRCTAGENTMappingRepositoryISpec extends BaseRepositoryISpec[AgentReferenceMapping, IRCTAGENTMappingRepository] with
  DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new IRCTAGENTMappingRepository(mongoComponent)
}
class IRPAYEAGENTMappingRepositoryISpec extends BaseRepositoryISpec[AgentReferenceMapping, IRPAYEAGENTMappingRepository] with
  DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new IRPAYEAGENTMappingRepository(mongoComponent)
}
class IRSDLTAGENTMappingRepositoryISpec extends BaseRepositoryISpec[AgentReferenceMapping, IRSDLTAGENTMappingRepository] with
DefaultPlayMongoRepositorySupport[AgentReferenceMapping] {
  override lazy val repository = new IRSDLTAGENTMappingRepository(mongoComponent)
}

abstract class BaseRepositoryISpec[
  T <: ArnToIdentifierMapping, R <: MappingRepository with RepositoryFunctions[T]: ClassTag]
  extends AnyWordSpecLike with Matchers with OptionValues with ScalaFutures with IntegrationPatience with GuiceOneAppPerSuite {

  override implicit lazy val app: Application = appBuilder.build()
  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(Map("migrate-repositories" -> "false","termination.stride.enrolment" -> "caat"))


  val arn1 = Arn("ARN00001")
  val arn2 = Arn("ARN00002")

  val utr1 = Utr("4000000009")
  val utr2 = Utr("7000000002")

  val reference1 = "Ref0001"
  val reference2 = "Ref0002"

  def repository: MappingRepository with RepositoryFunctions[T]

  private val repoName = repository.getClass.getSimpleName

  s"$repoName" should {
    behave like checkMapping(arn1, Seq(reference1, reference2))
    behave like checkMapping(utr1, Seq(reference1, reference2))

    def checkMapping(businessId: TaxIdentifier, references: Seq[String]): Unit = {
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
        e.getMessage() should include("E11000")
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

      val result: DeleteResult = repository.delete(arn1).futureValue
      result.getDeletedCount shouldBe 1L

      val mappingsAfterDelete: Seq[ArnToIdentifierMapping] = repository.findAll().futureValue
      mappingsAfterDelete.size shouldBe 1
    }

    "do not fail delete when no matching records exist for Arn" in {
      val mappings: Seq[ArnToIdentifierMapping] = repository.findAll().futureValue
      mappings.size shouldBe 0

      val result: DeleteResult = repository.delete(arn1).futureValue
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

      val result: DeleteResult = repository.delete(utr1).futureValue
      result.getDeletedCount shouldBe 1L

      val mappingsAfterDelete: Seq[ArnToIdentifierMapping] = repository.findAll().futureValue
      mappingsAfterDelete.size shouldBe 1
    }

    "do not fail delete when no matching records exist for Utr" in {
      val mappings: Seq[ArnToIdentifierMapping] = repository.findAll().futureValue
      mappings.size shouldBe 0

      val result: DeleteResult = repository.delete(utr1).futureValue
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
}
