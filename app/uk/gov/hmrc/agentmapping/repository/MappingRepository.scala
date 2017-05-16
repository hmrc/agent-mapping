/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json.Format
import play.api.libs.json.Json.{format, toJsFieldJsValueWrapper}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentmapping.repository.Mapping.formats
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


case class Mapping(arn: String, saAgentReference: String)

object Mapping extends ReactiveMongoFormats {
  implicit val formats: Format[Mapping] = format[Mapping]
}

@Singleton
class MappingRepository @Inject()(mongoComponent: ReactiveMongoComponent) extends
    ReactiveRepository[Mapping, BSONObjectID]("agent-mapping", mongoComponent.mongoConnector.db, formats, ReactiveMongoFormats.objectIdFormats) {
  def findBy(arn: Arn) :Future[List[Mapping]] = {
    find(Seq("arn" -> Some(arn)).map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get)): _*)
  }

  override def indexes = Seq(
    Index(Seq("arn" -> Ascending, "saAgentReference" -> Ascending), Some("arnAndAgentReference"), unique = true)
  )

  def createMapping(arn: Arn, saAgentReference: SaAgentReference)(implicit ec: ExecutionContext): Future[Unit] = {
    insert(Mapping(arn.value, saAgentReference.value)).map(_ => ())
  }
}
