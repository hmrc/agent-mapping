package uk.gov.hmrc.agentmapping.repository

import play.api.test.FakeApplication
import reactivemongo.api.commands.WriteResult
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentmapping.support.MongoApp
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class VatAgentReferenceMappingRepositoryISpec extends UnitSpec with MongoApp {
  override implicit lazy val app: FakeApplication = FakeApplication(
    additionalConfiguration = mongoConfiguration
  )

  val arn1 = Arn("ARN00001")
  val arn2 = Arn("ARN00002")

  val vatRegNo1 = "Ref0001"
  val vatRegNo2 = "Ref0002"

  def repo: VatAgentReferenceMappingRepository = app.injector.instanceOf[VatAgentReferenceMappingRepository]

  override def beforeEach() {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  "createMapping" should {
    "create a mapping" in {
      await(repo.createMapping(arn1, vatRegNo1))

      val result = await(repo.find())

      result.size shouldBe 1
      result.head.arn shouldBe arn1.value
      result.head.vrn shouldBe vatRegNo1

    }

    "not allow duplicate mappings to be created for the same ARN and SA Agent Reference" in {
      await(repo.createMapping(arn1, vatRegNo1))

      val e = intercept[DatabaseException] {
        await(repo.createMapping(arn1, vatRegNo1))
      }

      e.getMessage() should include ("E11000")
    }

    "allow more than one SA Agent Reference to be mapped to the same ARN" in {
      await(repo.createMapping(arn1, vatRegNo1))
      await(repo.createMapping(arn1, vatRegNo2))

      val result = await(repo.find())

      result.size shouldBe 2
      result.head.arn shouldBe arn1.value
      result.head.vrn shouldBe vatRegNo1
      result(1).arn shouldBe arn1.value
      result(1).vrn shouldBe vatRegNo2
    }
  }

  "findBy arn" should {
    "find all mpppings for an arn" in {
      await(repo.createMapping(arn1, vatRegNo1))
      await(repo.createMapping(arn1, vatRegNo2))
      await(repo.createMapping(arn2, vatRegNo2))

      val result:List[VatAgentReferenceMapping] = await(repo.findBy(arn1))

      result.size shouldBe 2
    }

    "return an empty list when no match is found" in {
      await(repo.createMapping(arn1, vatRegNo1))
      val result =await(repo.findBy(arn2))
      result.size shouldBe 0
    }
  }

  "delete by arn and SA Agent Reference" should {
    "delete a matching record" in {
      await(repo.createMapping(arn1, vatRegNo1))
      await(repo.createMapping(arn2, vatRegNo2))

      val mappings: List[VatAgentReferenceMapping] = await(repo.findAll())
      mappings.size shouldBe 2

      val result : WriteResult = await(repo.delete(arn1))
      result.code shouldBe None

      val mappingsAfterDelete: List[VatAgentReferenceMapping] = await(repo.findAll())
      mappingsAfterDelete.size shouldBe 1
    }

    "tolerate no matching record" in {
      val mappings: List[VatAgentReferenceMapping] = await(repo.findAll())
      mappings.size shouldBe 0

      val result : WriteResult = await(repo.delete(arn1))
      result.code shouldBe None
    }
  }
}
