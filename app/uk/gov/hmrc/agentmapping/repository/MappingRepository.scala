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

import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.{combine, set, unset}
import org.mongodb.scala.model.{IndexModel, IndexOptions, UpdateOptions}
import org.mongodb.scala.result.{DeleteResult, InsertOneResult, UpdateResult}
import play.api.libs.json.Format
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

//DO NOT DELETE (even if this microservice gets decommissioned)
abstract class MappingRepository(collectionName: String, identifierKey: String = "identifier", mongo: MongoComponent)(
  implicit ec: ExecutionContext
) extends PlayMongoRepository[AgentReferenceMapping](
      mongoComponent = mongo,
      collectionName = s"agent-mapping-${collectionName.toLowerCase}",
      domainFormat = AgentReferenceMapping.formats,
      indexes = List(
        IndexModel(
          ascending("arn", "identifier"),
          IndexOptions()
            .name("arnWithIdentifier")
            .unique(true)
            .partialFilterExpression(BsonDocument("arn" -> BsonDocument("$exists" -> true)))
        ),
        IndexModel(
          ascending("utr", identifierKey),
          IndexOptions()
            .name("utrWithIdentifier")
            .unique(true)
            .partialFilterExpression(BsonDocument("utr" -> BsonDocument("$exists" -> true)))
        ),
        IndexModel(
          ascending("arn"),
          IndexOptions()
            .name("AgentReferenceNumber")
        ),
        IndexModel(
          ascending("utr"),
          IndexOptions()
            .name("Utr")
        ),
        IndexModel(
          ascending("preCreatedDate"),
          IndexOptions()
            .name("preCreatedDate")
            .expireAfter(86400L, TimeUnit.SECONDS)
        )
      ),
      replaceIndexes = true,
      extraCodecs = List(
        Codecs.playFormatCodec(Format(Arn.arnReads, Arn.arnWrites)),
        Codecs.playFormatCodec(Format(Utr.utrReads, Utr.utrWrites))
      )
    ) {

  override lazy val requiresTtlIndex = false // keep data

  def findBy(arn: Arn): Future[Seq[AgentReferenceMapping]] =
    collection.find(equal("arn", arn.value)).toFuture()

  def findBy(utr: Utr): Future[Seq[AgentReferenceMapping]] =
    collection.find(equal("utr", utr.value)).toFuture()

  def findAll(): Future[Seq[AgentReferenceMapping]] =
    collection.find().toFuture()

  def store(
    identifier: TaxIdentifier,
    identifierValue: String,
    createdTime: Option[LocalDateTime] = Some(Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime)
  ): Future[InsertOneResult] =
    collection
      .insertOne(AgentReferenceMapping(identifier, identifierValue, createdTime))
      .toFuture()

  def updateUtrToArn(utr: Utr, arn: Arn): Future[UpdateResult] =
    collection
      .updateOne(
        equal("utr", utr.value),
        combine(set("arn", arn.value), unset("preCreatedDate"), unset("utr")),
        UpdateOptions().upsert(false)
      )
      .toFuture()

  def deleteByArn(arn: Arn): Future[DeleteResult] =
    collection.deleteOne(equal("arn", arn.value)).toFuture()

  def deleteByUtr(utr: Utr): Future[DeleteResult] =
    collection.deleteOne(equal("utr", utr.value)).toFuture()

  // This is for testing purposes only
  def deleteAll(): Future[DeleteResult] = collection.deleteMany(BsonDocument()).toFuture()
}
