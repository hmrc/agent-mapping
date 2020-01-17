package uk.gov.hmrc.agentmapping.repository

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentmapping.model.{AuthProviderId, GGTag, MappingDetails, MappingDetailsRepositoryRecord}
import uk.gov.hmrc.agentmapping.support.MongoApp
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class MappingDetailsRepositoryISpec extends UnitSpec with MongoSpecSupport with MongoApp {

  override implicit lazy val app: Application = appBuilder.build()
  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(Map("migrate-repositories" -> "false"))
      .configure(mongoConfiguration)

  private lazy val repo = app.injector.instanceOf[MappingDetailsRepository]

  override def beforeEach(): Unit =
    super.beforeEach()
  //await(repo.ensureIndexes)

  override def afterEach(): Unit =
    super.afterEach()

  private val arn = Arn("TARN0000001")
  private val authProviderId = AuthProviderId("cred-123")
  private val ggTag = GGTag("1234")
  private val count = 10
  private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
  private val dateTime: LocalDateTime = LocalDateTime.parse("2019-10-10 12:00", dateTimeFormatter)

  private val mappingDisplayDetails = MappingDetails(authProviderId, ggTag, count, dateTime)
  private val record = MappingDetailsRepositoryRecord(arn, Seq(mappingDisplayDetails))

  "MappingDisplayRepository" should {
    "create and findByArn" should {
      "create a record into the database and find it" in {
        await(repo.create(record))
        val createdRecord = await(repo.findByArn(arn))
        createdRecord.get.mappingDetails.head shouldBe mappingDisplayDetails
      }

      "ignore creation if trying to create another record with the same arn" in {
        val newRecordSameArn =
          MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, GGTag("2345"), 5, dateTime)))
        await(repo.create(record))
        await(repo.create(newRecordSameArn))
        await(repo.findByArn(arn)).get.mappingDetails.head shouldBe mappingDisplayDetails
      }

      "return none if the record is not there" in {
        val emptyRecord = await(repo.findByArn(arn))
        emptyRecord shouldBe None
      }
    }

    "updateMappingDisplayDetails" should {
      "update the mappings array with a new item" in {
        val newMappingDisplayDetails = MappingDetails(
          AuthProviderId("cred-456"),
          GGTag("5678"),
          20,
          LocalDateTime.parse("2019-11-11 13:00", dateTimeFormatter))

        await(repo.create(record))
        val initialFind = repo.findByArn(arn)

        await(repo.updateMappingDisplayDetails(arn, newMappingDisplayDetails))
        val updatedFind = repo.findByArn(arn)

        initialFind.get.mappingDetails.length shouldBe 1
        updatedFind.get.mappingDetails.length shouldBe 2
        updatedFind.get.mappingDetails.head shouldBe mappingDisplayDetails
        updatedFind.get.mappingDetails(1) shouldBe newMappingDisplayDetails
      }
    }

    "removeMappingDetailsForAgent" should {
      "remove mapping details record for given arn" in {
        await(repo.create(record))
        val initialFind = await(repo.findByArn(arn))

        await(repo.removeMappingDetailsForAgent(arn))
        val removedArn = await(repo.findByArn(arn))

        initialFind.get.mappingDetails.length shouldBe 1
        removedArn shouldBe None
      }
    }
  }
}
