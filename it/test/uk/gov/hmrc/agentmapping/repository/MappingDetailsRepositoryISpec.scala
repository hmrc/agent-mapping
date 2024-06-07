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

package test.uk.gov.hmrc.agentmapping.repository

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.agentmapping.model.{AuthProviderId, GGTag, MappingDetails, MappingDetailsRepositoryRecord}
import uk.gov.hmrc.agentmapping.repository.MappingDetailsRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.temporal.ChronoUnit.MILLIS
import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

class MappingDetailsRepositoryISpec
    extends AnyWordSpecLike
    with Matchers
    with DefaultPlayMongoRepositorySupport[MappingDetailsRepositoryRecord] {

  lazy val mappingDetailsRepository: MappingDetailsRepository = repository.asInstanceOf[MappingDetailsRepository]

  private val arn = Arn("TARN0000001")
  private val authProviderId = AuthProviderId("cred-123")
  private val ggTag = GGTag("1234")
  private val localDateTime: LocalDateTime = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime.truncatedTo(MILLIS)

  private val mappingDisplayDetails = MappingDetails(authProviderId, ggTag, 10, localDateTime)
  private val record = MappingDetailsRepositoryRecord(arn, Seq(mappingDisplayDetails))

  override val repository: PlayMongoRepository[MappingDetailsRepositoryRecord] = new MappingDetailsRepository(
    mongoComponent
  )

  "create and findByArn" should {
    "create a record into the database and find it" in {
      mappingDetailsRepository.create(record).futureValue
      val createdRecord = mappingDetailsRepository.findByArn(arn).futureValue
      createdRecord.get.mappingDetails.head shouldBe mappingDisplayDetails
    }

    "ignore creation if trying to create another record with the same arn" in {
      val newRecordSameArn =
        MappingDetailsRepositoryRecord(arn, Seq(MappingDetails(authProviderId, GGTag("2345"), 5, localDateTime)))

      mappingDetailsRepository.create(record).futureValue
      mappingDetailsRepository.create(newRecordSameArn).futureValue
      mappingDetailsRepository.findByArn(arn).futureValue.get.mappingDetails.head shouldBe mappingDisplayDetails
    }

    "return none if the record is not there" in {
      val emptyRecord = mappingDetailsRepository.findByArn(arn).futureValue
      emptyRecord shouldBe None
    }
  }

  "updateMappingDisplayDetails" should {
    "update the mappings array with a new item" in {
      val newMappingDisplayDetails =
        MappingDetails(AuthProviderId("cred-456"), GGTag("5678"), 20, LocalDateTime.parse("2019-11-11T13:00:00.00"))

      mappingDetailsRepository.create(record).futureValue
      val initialFind = mappingDetailsRepository.findByArn(arn).futureValue

      mappingDetailsRepository.updateMappingDisplayDetails(arn, newMappingDisplayDetails).futureValue
      val updatedFind = mappingDetailsRepository.findByArn(arn).futureValue

      initialFind.get.mappingDetails.length shouldBe 1
      updatedFind.get.mappingDetails.length shouldBe 2
      updatedFind.get.mappingDetails.head shouldBe mappingDisplayDetails
      updatedFind.get.mappingDetails(1) shouldBe newMappingDisplayDetails
    }
  }

  "removeMappingDetailsForAgent" should {
    "remove mapping details record for given arn" in {
      mappingDetailsRepository.create(record).futureValue
      val initialFind = mappingDetailsRepository.findByArn(arn).futureValue

      mappingDetailsRepository.removeMappingDetailsForAgent(arn).futureValue
      val removedArn = mappingDetailsRepository.findByArn(arn).futureValue

      initialFind.get.mappingDetails.length shouldBe 1
      removedArn shouldBe None
    }
  }
}
