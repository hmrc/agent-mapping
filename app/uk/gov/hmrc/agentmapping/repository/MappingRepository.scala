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
import play.api.libs.json.{Format, Json}
import play.api.libs.json.Json.{JsValueWrapper, toJsFieldJsValueWrapper}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.mongo.{AtomicUpdate, ReactiveRepository}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import reactivemongo.play.json.ImplicitBSONHandlers._

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

trait MappingRepository {
  def store(identifier: TaxIdentifier, identifierValue: String)(implicit ec: ExecutionContext): Future[Unit]
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
  wrap: (TaxIdentifier, String) => T)(implicit mongoComponent: ReactiveMongoComponent)
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

  override def indexes =
    Seq(
      Index(Seq("arn" -> Ascending, identifierKey -> Ascending), Some("arnAndIdentifier"), unique = true),
      Index(Seq("utr" -> Ascending, identifierKey -> Ascending), Some("utrAndIdentifier"), unique = true),
      Index(Seq("arn" -> Ascending), Some("AgentReferenceNumber")),
      Index(Seq("utr" -> Ascending), Some("Utr"))
    )

  def store(identifier: TaxIdentifier, identifierValue: String)(implicit ec: ExecutionContext): Future[Unit] =
    insert(wrap(identifier, identifierValue)).map(_ => ())

  def updateUtrToArn(utr: Utr, arn: Arn)(implicit ec: ExecutionContext): Future[Unit] = {
    val selector = Json.obj("utr" -> utr.value)
    val update = Json.obj(
      "$set" -> Json.obj("arn" -> arn.value)
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
