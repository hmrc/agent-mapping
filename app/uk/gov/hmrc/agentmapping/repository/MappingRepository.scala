/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.Inject
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json.{JsValueWrapper, toJsFieldJsValueWrapper}
import play.api.libs.json.{Format, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.commands.bson.DefaultBSONCommandError
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

trait MappingRepository {
  def store(
    identifier: TaxIdentifier,
    identifierValue: String,
    createdTime: Option[DateTime] = Some(DateTime.now(DateTimeZone.UTC)))(implicit ec: ExecutionContext): Future[Unit]

  def updateUtrToArn(utr: Utr, arn: Arn)(implicit ec: ExecutionContext): Future[Unit]
}

trait RepositoryFunctions[T] {
  def find(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[T]]
  def findBy(arn: Arn)(implicit ec: ExecutionContext): Future[List[T]]
  def findBy(utr: Utr)(implicit ec: ExecutionContext): Future[List[T]]
  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(
    implicit ec: ExecutionContext): Future[List[T]]
  def delete(arn: Arn)(implicit ec: ExecutionContext): Future[WriteResult]
  def delete(utr: Utr)(implicit ec: ExecutionContext): Future[WriteResult]
  def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]]
}

abstract class BaseMappingRepository[T: Format: Manifest](
  collectionName: String,
  identifierKey: String,
  wrap: (TaxIdentifier, String, Option[DateTime]) => T)(implicit mongoComponent: ReactiveMongoComponent)
    extends ReactiveRepository[T, BSONObjectID](
      collectionName,
      mongoComponent.mongoConnector.db,
      implicitly[Format[T]],
      ReactiveMongoFormats.objectIdFormats)
    with MappingRepository
    with StrictlyEnsureIndexes[T, BSONObjectID]
    with RepositoryFunctions[T] {

  def findBy(arn: Arn)(implicit ec: ExecutionContext): Future[List[T]] =
    find(Seq("arn" -> Some(arn)).map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get)): _*)

  def findBy(utr: Utr)(implicit ec: ExecutionContext): Future[List[T]] =
    find(Seq("utr" -> Some(utr)).map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get)): _*)

  // TODO: Remove this overridden method once the service has been deployed and the indexes have been updated.
  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    // Drop the old "arnAndIdentifier" index as we will replace it with an "arnWithIdentifier" index with different options
    // Drop the old "utrAndIdentifier" index as we will replace it with an "utrWithIdentifier" index with different options

    def dropOldIndexIfExists(indexName: String) =
      collection.indexesManager
        .drop(indexName)
        .map { numIndexesDropped =>
          if (numIndexesDropped >= 0) {
            logger.info(s"Successfully dropped old index $indexName")
          } else {
            logger.warn(s"Did not drop old index $indexName")
          }
        }
        .recover {
          case t: DefaultBSONCommandError if t.code.contains(27) =>
            logger.info(s"IndexNotFound: Did not drop old index '$indexName' as it was not found")
          case t =>
            logger.warn(s"Did not drop old index '$indexName'", t)
        }

    for {
      _      <- dropOldIndexIfExists("utrAndIdentifier")
      _      <- dropOldIndexIfExists("arnAndIdentifier")
      result <- super.ensureIndexes
    } yield result
  }

  override def indexes =
    Seq(
      Index(
        Seq("arn" -> Ascending, identifierKey -> Ascending),
        Some("arnWithIdentifier"),
        unique = true,
        partialFilter = Some(BSONDocument("arn" -> BSONDocument("$exists" -> true)))
      ),
      Index(
        Seq("utr" -> Ascending, identifierKey -> Ascending),
        Some("utrWithIdentifier"),
        unique = true,
        partialFilter = Some(BSONDocument("utr" -> BSONDocument("$exists" -> true)))
      ),
      Index(
        Seq("arn" -> Ascending),
        Some("AgentReferenceNumber")
      ),
      Index(
        Seq("utr" -> Ascending),
        Some("Utr")
      ),
      Index(
        Seq("preCreatedDate" -> Ascending),
        Some("preCreatedDate"),
        options = BSONDocument("expireAfterSeconds" -> 86400)
      )
    )

  def store(
    identifier: TaxIdentifier,
    identifierValue: String,
    createdTime: Option[DateTime] = Some(DateTime.now(DateTimeZone.UTC)))(implicit ec: ExecutionContext): Future[Unit] =
    insert(wrap(identifier, identifierValue, createdTime)).map(_ => ())

  def updateUtrToArn(utr: Utr, arn: Arn)(implicit ec: ExecutionContext): Future[Unit] = {
    val selector = Json.obj("utr" -> utr.value)
    val update = Json.obj(
      "$set"   -> Json.obj("arn"            -> arn.value),
      "$unset" -> Json.obj("preCreatedDate" -> "")
    )

    collection
      .update(selector, update, upsert = false)
      .map(_ => ())
  }

  def delete(arn: Arn)(implicit ec: ExecutionContext): Future[WriteResult] =
    remove("arn" -> arn.value)

  def delete(utr: Utr)(implicit ec: ExecutionContext): Future[WriteResult] =
    remove("utr" -> utr.value)
}

abstract class NewMappingRepository @Inject()(serviceName: String)(implicit mongoComponent: ReactiveMongoComponent)
    extends BaseMappingRepository(
      s"agent-mapping-${serviceName.toLowerCase}",
      "identifier",
      AgentReferenceMapping.apply)
