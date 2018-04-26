package uk.gov.hmrc.agentmapping.repository

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import reactivemongo.api.commands.WriteResult
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentmapping.support.MongoApp
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

// REMOVE AFTER DB MIGRATION - start
class SaAgentReferenceMappingRepositoryISpec extends BaseRepositoryISpec[SaAgentReferenceMapping, SaAgentReferenceMappingRepository]
class AgentCodeMappingRepositoryISpec extends BaseRepositoryISpec[AgentCodeMapping, AgentCodeMappingRepository]
class VatAgentReferenceMappingRepositoryISpec extends BaseRepositoryISpec[VatAgentReferenceMapping, VatAgentReferenceMappingRepository]
// REMOVE AFTER DB MIGRATION - end

class IRSAAGENTMappingRepositoryISpec extends BaseRepositoryISpec[AgentReferenceMapping, IRSAAGENTMappingRepository]
class NewAgentCodeMappingRepositoryISpec extends BaseRepositoryISpec[AgentReferenceMapping, NewAgentCodeMappingRepository]
class HMCEVATAGNTMappingRepositoryISpec extends BaseRepositoryISpec[AgentReferenceMapping, HMCEVATAGNTMappingRepository]

abstract class BaseRepositoryISpec[T <: ArnToIdentifierMapping, R <: MappingRepository with RepositoryFunctions[T]: ClassTag]
  extends UnitSpec with MongoApp {

  override implicit lazy val app: Application = appBuilder.build()
  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(Map("migrate-repositories" -> "false"))
      .configure(mongoConfiguration)

  val arn1 = Arn("ARN00001")
  val arn2 = Arn("ARN00002")

  val reference1 = "Ref0001"
  val reference2 = "Ref0002"

  def repository: MappingRepository with RepositoryFunctions[T] = app.injector.instanceOf[R]

  override def beforeEach() {
    super.beforeEach()
    await(repository.ensureIndexes)
  }

  private val repoName = repository.getClass.getSimpleName

  s"$repoName" should {

    "create a mapping" in {
      await(repository.store(arn1, reference1))

      val result = await(repository.find())

      result.size shouldBe 1
      result.head.arn shouldBe arn1.value
      result.head.identifier shouldBe reference1

    }

    "not allow duplicate mappings to be created for the same ARN and identifier" in {
      await(repository.store(arn1, reference1))

      val e = intercept[DatabaseException] {
        await(repository.store(arn1, reference1))
      }

      e.getMessage() should include("E11000")
    }

    "allow more than one identifier to be mapped to the same ARN" in {
      await(repository.store(arn1, reference1))
      await(repository.store(arn1, reference2))

      val result = await(repository.find())

      result.size shouldBe 2
      result.head.arn shouldBe arn1.value
      result.head.identifier shouldBe reference1
      result(1).arn shouldBe arn1.value
      result(1).identifier shouldBe reference2
    }

    "find all mappings for an arn" in {
      await(repository.store(arn1, reference1))
      await(repository.store(arn1, reference2))
      await(repository.store(arn2, reference2))

      val result: List[ArnToIdentifierMapping] = await(repository.findBy(arn1))

      result.size shouldBe 2
    }

    "return an empty list when no match is found" in {
      await(repository.store(arn1, reference1))
      val result = await(repository.findBy(arn2))
      result.size shouldBe 0
    }

    "delete a matching records by arn" in {
      await(repository.store(arn1, reference1))
      await(repository.store(arn2, reference2))

      val mappings: List[ArnToIdentifierMapping] = await(repository.findAll())
      mappings.size shouldBe 2

      val result: WriteResult = await(repository.delete(arn1))
      result.code shouldBe None

      val mappingsAfterDelete: List[ArnToIdentifierMapping] = await(repository.findAll())
      mappingsAfterDelete.size shouldBe 1
    }

    "do not fail delete when no matching records exist" in {
      val mappings: List[ArnToIdentifierMapping] = await(repository.findAll())
      mappings.size shouldBe 0

      val result: WriteResult = await(repository.delete(arn1))
      result.code shouldBe None
    }
  }

}
