package uk.gov.hmrc.agentmapping.repository

import play.api.test.FakeApplication
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.support.MongoApp
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class MappingRepositoryISpec extends UnitSpec with MongoApp {
  override implicit lazy val app: FakeApplication = FakeApplication(
    additionalConfiguration = mongoConfiguration
  )

  val arn = Arn("ARN00001")
  val saAgentReference = SaAgentReference("Ref0001")

  def repo: MappingRepository = app.injector.instanceOf[MappingRepository]

  override def beforeEach() {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  "createMapping" should {
    "create a mapping" in {
      await(repo.createMapping(arn, saAgentReference))

      val result = await(repo.find())

      result.size shouldBe 1
      result.head.arn shouldBe arn.value
      result.head.saAgentReference shouldBe saAgentReference.value

    }

    "not allow duplicate mappings to be created for the same ARN and SA Agent Reference" in {
      await(repo.createMapping(arn, saAgentReference))

      val e = intercept[DatabaseException] {
        await(repo.createMapping(arn, saAgentReference))
      }

      e.getMessage() should include ("E11000")
    }

    "allow more than one SA Agent Reference to be mapped to the same ARN" in {
      val saAgentReference1 = SaAgentReference("REF0002")
      await(repo.createMapping(arn, saAgentReference))
      await(repo.createMapping(arn, saAgentReference1))

      val result = await(repo.find())

      result.size shouldBe 2
      result.head.arn shouldBe arn.value
      result.head.saAgentReference shouldBe saAgentReference.value
      result(1).arn shouldBe arn.value
      result(1).saAgentReference shouldBe saAgentReference1.value
    }
  }
}
