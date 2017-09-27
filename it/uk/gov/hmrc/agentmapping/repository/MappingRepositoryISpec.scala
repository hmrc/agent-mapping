package uk.gov.hmrc.agentmapping.repository

import play.api.test.FakeApplication
import reactivemongo.api.commands.WriteResult
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentmapping.support.MongoApp
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class MappingRepositoryISpec extends UnitSpec with MongoApp {
  override implicit lazy val app: FakeApplication = FakeApplication(
    additionalConfiguration = mongoConfiguration
  )

  val arn1 = Arn("ARN00001")
  val arn2 = Arn("ARN00002")

  val saAgentReference1 = SaAgentReference("Ref0001")
  val saAgentReference2 = SaAgentReference("Ref0002")

  def repo: MappingRepository = app.injector.instanceOf[MappingRepository]

  override def beforeEach() {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  "createMapping" should {
    "create a mapping" in {
      await(repo.createMapping(arn1, saAgentReference1))

      val result = await(repo.find())

      result.size shouldBe 1
      result.head.arn shouldBe arn1.value
      result.head.saAgentReference shouldBe saAgentReference1.value

    }

    "not allow duplicate mappings to be created for the same ARN and SA Agent Reference" in {
      await(repo.createMapping(arn1, saAgentReference1))

      val e = intercept[DatabaseException] {
        await(repo.createMapping(arn1, saAgentReference1))
      }

      e.getMessage() should include ("E11000")
    }

    "allow more than one SA Agent Reference to be mapped to the same ARN" in {
      await(repo.createMapping(arn1, saAgentReference1))
      await(repo.createMapping(arn1, saAgentReference2))

      val result = await(repo.find())

      result.size shouldBe 2
      result.head.arn shouldBe arn1.value
      result.head.saAgentReference shouldBe saAgentReference1.value
      result(1).arn shouldBe arn1.value
      result(1).saAgentReference shouldBe saAgentReference2.value
    }
  }

  "findBy arn" should {
    "find all mpppings fpr an arn" in {
      await(repo.createMapping(arn1, saAgentReference1))
      await(repo.createMapping(arn1, saAgentReference2))
      await(repo.createMapping(arn2, saAgentReference2))

      val result:List[Mapping] = await(repo.findBy(arn1))

      result.size shouldBe 2
    }

    "return an empty list when no match is found" in {
      await(repo.createMapping(arn1, saAgentReference1))
      val result =await(repo.findBy(arn2))
      result.size shouldBe 0
    }
  }

  "delete by arn and SA Agent Reference" should {
    "delete a matching record" in {
      await(repo.createMapping(arn1, saAgentReference1))
      await(repo.createMapping(arn2, saAgentReference2))

      val mappings: List[Mapping] = await(repo.findAll())
      mappings.size shouldBe 2

      val result : WriteResult = await(repo.delete(arn1))
      result.code shouldBe None

      val mappingsAfterDelete: List[Mapping] = await(repo.findAll())
      mappingsAfterDelete.size shouldBe 1
    }

    "tolerate no matching record" in {
      val mappings: List[Mapping] = await(repo.findAll())
      mappings.size shouldBe 0

      val result : WriteResult = await(repo.delete(arn1))
      result.code shouldBe None
    }
  }
}
