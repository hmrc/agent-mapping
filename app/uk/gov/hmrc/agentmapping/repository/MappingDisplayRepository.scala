/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentmapping.model.{MappingDisplayDetails, MappingDisplayRepositoryRecord}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import reactivemongo.play.json.ImplicitBSONHandlers._

import scala.concurrent.{ExecutionContext, Future}

trait MappingDisplayRepository {
  def findByArn(arn: Arn)(implicit ec: ExecutionContext): Future[Option[MappingDisplayRepositoryRecord]]
  def create(mappingDisplayRepositoryRecord: MappingDisplayRepositoryRecord)(
    implicit ec: ExecutionContext): Future[Unit]
  def updateMappingDisplayDetails(arn: Arn, newMapping: MappingDisplayDetails)(
    implicit ec: ExecutionContext): Future[Unit]

  implicit class WriteResultChecker(future: Future[WriteResult]) {
    def checkResult(implicit ec: ExecutionContext): Future[Unit] = future.map { writeResult =>
      if (hasProblems(writeResult)) throw new RuntimeException(writeResult.toString)
      else ()
    }
  }

  private def hasProblems(writeResult: WriteResult): Boolean =
    !writeResult.ok || writeResult.writeErrors.nonEmpty || writeResult.writeConcernError.isDefined
}

@Singleton
class MappingDisplayRespositoryImpl @Inject()(mongoComponent: ReactiveMongoComponent)
    extends ReactiveRepository[MappingDisplayRepositoryRecord, BSONObjectID](
      collectionName = "mapping-display-details",
      mongoComponent.mongoConnector.db,
      MappingDisplayRepositoryRecord.mappingDisplayRepositoryFormat,
      ReactiveMongoFormats.objectIdFormats
    )
    with MappingDisplayRepository {

  override def indexes: Seq[Index] =
    Seq(
      Index(key = Seq("arn" -> IndexType.Ascending), name = Some("arn"), unique = true)
    )

  def create(mappingDisplayRepositoryRecord: MappingDisplayRepositoryRecord)(
    implicit ec: ExecutionContext): Future[Unit] =
    collection.insert(ordered = false).one(mappingDisplayRepositoryRecord).checkResult

  def findByArn(arn: Arn)(implicit ec: ExecutionContext): Future[Option[MappingDisplayRepositoryRecord]] =
    find("arn" -> arn).map(_.headOption)

  def updateMappingDisplayDetails(arn: Arn, newMapping: MappingDisplayDetails)(
    implicit ec: ExecutionContext): Future[Unit] = {
    val updateOp = Json.obj("$addToSet" -> (Json.obj("mappings" -> newMapping)))
    collection.update(ordered = false).one(Json.obj("arn" -> arn.value), updateOp).checkResult
  }
}
