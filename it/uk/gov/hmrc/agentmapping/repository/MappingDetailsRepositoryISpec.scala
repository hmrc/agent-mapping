package uk.gov.hmrc.agentmapping.repository

import com.google.inject.AbstractModule
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentmapping.model.{AuthProviderId, GGTag, MappingDetails, MappingDetailsRepositoryRecord}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class MappingDetailsRepositoryISpec extends AnyWordSpecLike with Matchers with OptionValues
  with ScalaFutures with DefaultPlayMongoRepositorySupport[MappingDetailsRepositoryRecord] {

  implicit lazy val app: Application = appBuilder.build()

  override lazy val repository = new MappingDetailsRepository(mongoComponent)

  val moduleWithOverrides: AbstractModule = new AbstractModule() {
    override def configure(): Unit = {
      bind(classOf[MappingDetailsRepository]).toInstance(repository)
    }
  }

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .disable[com.kenshoo.play.metrics.PlayModule]
      .configure(Map("migrate-repositories" -> "false"))
      .overrides(moduleWithOverrides)

  private lazy val repo = app.injector.instanceOf[MappingDetailsRepository]

  private val arn = Arn("TARN0000001")
  private val authProviderId = AuthProviderId("cred-123")
  private val ggTag = GGTag("1234")
  private val localDateTime: LocalDateTime = LocalDateTime.now()

  private val mappingDisplayDetails = MappingDetails(authProviderId, ggTag, 10, localDateTime)
  private val record = MappingDetailsRepositoryRecord(arn, Seq(mappingDisplayDetails))

  "MappingDisplayRepository" should {
    "create and findByArn" should {
      "create a record into the database and find it" in {
        repo.create(record).futureValue
        val createdRecord = repo.findByArn(arn).futureValue
        createdRecord.get.mappingDetails.head shouldBe mappingDisplayDetails
      }

      "ignore creation if trying to create another record with the same arn" in {
        val newRecordSameArn =
          MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, GGTag("2345"), 5, localDateTime)))
        repo.create(record).futureValue
        repo.create(newRecordSameArn).futureValue
        repo.findByArn(arn).futureValue.get.mappingDetails.head shouldBe mappingDisplayDetails
      }

      "return none if the record is not there" in {
        val emptyRecord = repo.findByArn(arn).futureValue
        emptyRecord shouldBe None
      }
    }

    "updateMappingDisplayDetails" should {
      "update the mappings array with a new item" in {
        val newMappingDisplayDetails = MappingDetails(
          AuthProviderId("cred-456"),
          GGTag("5678"),
          20,
          LocalDateTime.parse("2019-11-11T13:00:00.00"))

        repo.create(record).futureValue
        val initialFind = repo.findByArn(arn).futureValue

        repo.updateMappingDisplayDetails(arn, newMappingDisplayDetails).futureValue
        val updatedFind = repo.findByArn(arn).futureValue

        initialFind.get.mappingDetails.length shouldBe 1
        updatedFind.get.mappingDetails.length shouldBe 2
        updatedFind.get.mappingDetails.head shouldBe mappingDisplayDetails
        updatedFind.get.mappingDetails(1) shouldBe newMappingDisplayDetails
      }
    }

    "removeMappingDetailsForAgent" should {
      "remove mapping details record for given arn" in {
        repo.create(record).futureValue
        val initialFind = repo.findByArn(arn).futureValue

        repo.removeMappingDetailsForAgent(arn).futureValue
        val removedArn = repo.findByArn(arn).futureValue

        initialFind.get.mappingDetails.length shouldBe 1
        removedArn shouldBe None
      }
    }
  }
}
