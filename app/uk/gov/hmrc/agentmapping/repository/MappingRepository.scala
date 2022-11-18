/*
 * Copyright 2022 HM Revenue & Customs
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
import org.mongodb.scala.result.DeleteResult
import play.api.libs.json.Format
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

trait MappingRepository {
  def store(
    identifier: TaxIdentifier,
    identifierValue: String,
    createdTime: Option[LocalDateTime] = Some(LocalDateTime.now())): Future[Unit]

  def updateUtrToArn(utr: Utr, arn: Arn): Future[Unit]
}

trait RepositoryFunctions[T] {
  def find(key: String, value: String): Future[Seq[T]]
  def findBy(arn: Arn): Future[Seq[T]]
  def findBy(utr: Utr): Future[Seq[T]]
  def findAll(): Future[Seq[T]]
  def delete(arn: Arn): Future[DeleteResult]
  def delete(utr: Utr): Future[DeleteResult]
  def ensureDbIndexes(implicit ec: ExecutionContext): Future[Seq[String]]
}

object RepositoryTools {

  def dropIndexIfExists[A](rr: PlayMongoRepository[A], indexName: String)(implicit ec: ExecutionContext): Future[Unit] =
    rr.collection.dropIndex(indexName).toFuture().map(_ => ()).recover {
      case ex: Exception => println(s"index could not be found")
    }
}

abstract class BaseMappingRepository[T: Format: Manifest](
  collectionName: String,
  identifierKey: String,
  mongo: MongoComponent,
  wrap: (TaxIdentifier, String, Option[LocalDateTime]) => T)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[T](
      mongoComponent = mongo,
      collectionName = collectionName,
      domainFormat = implicitly[Format[T]],
      indexes = Seq(
        IndexModel(
          ascending("arn", identifierKey),
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
            .name("AgentReferenceNumber")),
        IndexModel(
          ascending("utr"),
          IndexOptions()
            .name("Utr")),
        IndexModel(
          ascending("preCreatedDate"),
          IndexOptions()
            .name("preCreatedDate")
            .expireAfter(86400L, TimeUnit.SECONDS)),
      ),
      replaceIndexes = false
    )
    with MappingRepository
    with RepositoryFunctions[T] {

  override def find(key: String, value: String): Future[Seq[T]] =
    collection.find(equal(key, value)).toFuture()

  import RepositoryTools._
  // TODO: Remove this overridden method once the service has been deployed and the indexes have been updated.
  override def ensureDbIndexes(implicit ec: ExecutionContext): Future[Seq[String]] =
    for {
      _      <- dropIndexIfExists(this, "utrAndIdentifier")
      _      <- dropIndexIfExists(this, "arnAndIdentifier")
      result <- super.ensureIndexes
    } yield result

  override def findBy(arn: Arn): Future[Seq[T]] =
    collection.find(equal("arn", arn.value)).toFuture()

  override def findBy(utr: Utr): Future[Seq[T]] =
    collection.find(equal("utr", utr.value)).toFuture()

  override def findAll(): Future[Seq[T]] =
    collection.find().toFuture()

  override def store(
    identifier: TaxIdentifier,
    identifierValue: String,
    createdTime: Option[LocalDateTime] = Some(LocalDateTime.now())): Future[Unit] = {
    println(s"STORES CALLED >>>>>>>>>>$identifier value $identifierValue")
    collection
      .insertOne(wrap(identifier, identifierValue, createdTime))
      .toFuture()
      .map(res => println(s">>>>>>>>>>>>>>>>>>>>>>>>>>>>>INSERTED ONE RES $res"))
  }

  override def updateUtrToArn(utr: Utr, arn: Arn): Future[Unit] =
    collection
      .updateOne(
        equal("utr", utr.value),
        combine(set("arn", arn.value), unset("preCreatedDate"), unset("utr")),
        UpdateOptions().upsert(false))
      .toFuture()
      .map(_ => ())

  override def delete(arn: Arn): Future[DeleteResult] =
    collection.deleteOne(equal("arn", arn.value)).toFuture()

  override def delete(utr: Utr): Future[DeleteResult] =
    collection.deleteOne(equal("utr", utr.value)).toFuture()
}

abstract class NewMappingRepository @Inject()(serviceName: String, mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends BaseMappingRepository(
      s"agent-mapping-${serviceName.toLowerCase}",
      "identifier",
      mongo,
      AgentReferenceMapping.apply)
