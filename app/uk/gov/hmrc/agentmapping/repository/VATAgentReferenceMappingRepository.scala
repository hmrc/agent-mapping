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

import javax.inject.{Inject, Singleton}

import play.api.libs.json.Format
import play.api.libs.json.Json.{format, toJsFieldJsValueWrapper}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

case class VatAgentReferenceMapping(arn: String, vatRegNo: String)

object VatAgentReferenceMapping extends ReactiveMongoFormats {
  implicit val formats: Format[VatAgentReferenceMapping] = format[VatAgentReferenceMapping]
}

@Singleton
class VatAgentReferenceMappingRepository @Inject()(mongoComponent: ReactiveMongoComponent) extends
    ReactiveRepository[VatAgentReferenceMapping, BSONObjectID]("agent-mapping-vat", mongoComponent.mongoConnector.db, VatAgentReferenceMapping.formats, ReactiveMongoFormats.objectIdFormats)
    with MappingRepository {

  def findBy(arn: Arn)(implicit ec: ExecutionContext): Future[List[VatAgentReferenceMapping]] = {
    find(Seq("arn" -> Some(arn)).map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get)): _*)
  }

  override def indexes = Seq(
    Index(Seq("arn" -> Ascending, "vatRegNo" -> Ascending), Some("arnAndVatRegNo"), unique = true),
    Index(Seq("arn" -> Ascending), Some("AgentReferenceNumber"))
  )

  def createMapping(arn: Arn, identifierValue: String)(implicit ec: ExecutionContext): Future[Unit] = {
    insert(VatAgentReferenceMapping(arn.value, identifierValue)).map(_ => ())
  }

  def delete(arn: Arn)(implicit ec: ExecutionContext): Future[WriteResult] = {
    remove("arn" -> arn.value)
  }
}
