/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Updates
import play.api.Logging
import play.api.libs.json.Format
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.model.MappingDetails
import uk.gov.hmrc.agentmapping.model.MappingDetailsRepositoryRecord
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

//DO NOT DELETE (even if this microservice gets decommissioned)
@Singleton
class MappingDetailsRepository @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
extends PlayMongoRepository[MappingDetailsRepositoryRecord](
  mongoComponent = mongo,
  collectionName = "mapping-details",
  domainFormat = MappingDetailsRepositoryRecord.mappingDisplayRepositoryFormat,
  indexes = List(
    IndexModel(ascending("arn"), IndexOptions().name("arn").unique(true)),
    IndexModel(
      ascending("mappingDetails.authProviderId"),
      IndexOptions()
        .name("authProviderIdSparse")
        .sparse(true)
    )
  ),
  replaceIndexes = true,
  extraCodecs = List(
    Codecs.playFormatCodec(MappingDetails.mongoDisplayDetailsFormat),
    Codecs.playFormatCodec(Format(Arn.arnReads, Arn.arnWrites))
  )
)
with Logging {

  override lazy val requiresTtlIndex = false // keep data to show the user when they next visit.

  def create(mappingDisplayRepositoryRecord: MappingDetailsRepositoryRecord): Future[Unit] = collection
    .insertOne(mappingDisplayRepositoryRecord)
    .toFuture()
    .map(_ => ())
    .recover { case e: MongoWriteException => logger.warn(s"Error trying to insert a mapping details record ${e.getError}") }

  def findByArn(arn: Arn): Future[Option[MappingDetailsRepositoryRecord]] = collection
    .find(equal("arn", arn.value))
    .headOption()

  def updateMappingDisplayDetails(
    arn: Arn,
    newMapping: MappingDetails
  ): Future[Unit] = collection
    .updateOne(equal("arn", arn.value), Updates.addToSet("mappingDetails", newMapping))
    .toFuture()
    .map {
      case result if result.getMatchedCount == 1L => ()
      case e => logger.error(s"Unknown error occurred when updating mapping details ${e.wasAcknowledged()}.")
    }

  def removeMappingDetailsForAgent(arn: Arn)(implicit ec: ExecutionContext): Future[Int] = collection
    .deleteOne(equal("arn", arn))
    .toFuture()
    .map(deleteResult => deleteResult.getDeletedCount.toInt)

}
