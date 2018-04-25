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

import javax.inject.{ Inject, Singleton }
import play.api.libs.json.Format
import play.api.libs.json.Json.{ JsValueWrapper, format, toJsFieldJsValueWrapper }
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.collection.Seq
import scala.concurrent.{ ExecutionContext, Future }

trait MappingRepository {
  def store(arn: Arn, identifierValue: String)(implicit ec: ExecutionContext): Future[Unit]
}

trait RepositoryFunctions[T] {
  def find(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[T]]
  def findBy(arn: Arn)(implicit ec: ExecutionContext): Future[List[T]]
  def findAll(readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[List[T]]
  def delete(arn: Arn)(implicit ec: ExecutionContext): Future[WriteResult]
  def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]]
}

abstract class BaseMappingRepository[T: Format: Manifest](
  collectionName: String,
  identifierKey: String,
  wrap: (String, String) => T)(implicit mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[T, BSONObjectID](
    collectionName,
    mongoComponent.mongoConnector.db,
    implicitly[Format[T]],
    ReactiveMongoFormats.objectIdFormats)
  with MappingRepository with StrictlyEnsureIndexes[T, BSONObjectID] {

  def findBy(arn: Arn)(implicit ec: ExecutionContext): Future[List[T]] = {
    find(Seq("arn" -> Some(arn)).map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get)): _*)
  }

  override def indexes = Seq(
    Index(Seq("arn" -> Ascending, identifierKey -> Ascending), Some("arnAndIdentifier"), unique = true),
    Index(Seq("arn" -> Ascending), Some("AgentReferenceNumber")))

  def store(arn: Arn, identifierValue: String)(implicit ec: ExecutionContext): Future[Unit] = {
    insert(wrap(arn.value, identifierValue)).map(_ => ())
  }

  def delete(arn: Arn)(implicit ec: ExecutionContext): Future[WriteResult] = {
    remove("arn" -> arn.value)
  }

}

trait ArnToIdentifierMapping {
  def arn: String
  def identifier: String
}

//New repositories

case class AgentReferenceMapping(arn: String, identifier: String) extends ArnToIdentifierMapping

object AgentReferenceMapping extends ReactiveMongoFormats {
  implicit val formats: Format[AgentReferenceMapping] = format[AgentReferenceMapping]
}

abstract class NewMappingRepository @Inject() (serviceName: String)(implicit mongoComponent: ReactiveMongoComponent)
  extends BaseMappingRepository(s"agent-mapping-$serviceName", "identifier", AgentReferenceMapping.apply)
  with RepositoryFunctions[AgentReferenceMapping]

@Singleton
class IRSAAGENTMappingRepository @Inject() (implicit mongoComponent: ReactiveMongoComponent)
  extends NewMappingRepository("ir-sa-agent")

@Singleton
class NewAgentCodeMappingRepository @Inject() (implicit mongoComponent: ReactiveMongoComponent)
  extends NewMappingRepository("agentcode")

@Singleton
class HMCEVATAGNTMappingRepository @Inject() (implicit mongoComponent: ReactiveMongoComponent)
  extends NewMappingRepository("hmce-vat-agnt")

//Old repositories

case class SaAgentReferenceMapping(arn: String, saAgentReference: String) extends ArnToIdentifierMapping {
  override def identifier: String = saAgentReference
}

object SaAgentReferenceMapping extends ReactiveMongoFormats {
  implicit val formats: Format[SaAgentReferenceMapping] = format[SaAgentReferenceMapping]
}

@Singleton
class SaAgentReferenceMappingRepository @Inject() (implicit mongoComponent: ReactiveMongoComponent)
  extends BaseMappingRepository("agent-mapping", "saAgentReference", SaAgentReferenceMapping.apply)
  with RepositoryFunctions[SaAgentReferenceMapping]

case class AgentCodeMapping(arn: String, agentCode: String) extends ArnToIdentifierMapping {
  override def identifier: String = agentCode
}

object AgentCodeMapping extends ReactiveMongoFormats {
  implicit val formats: Format[AgentCodeMapping] = format[AgentCodeMapping]
}

@Singleton
class AgentCodeMappingRepository @Inject() (implicit mongoComponent: ReactiveMongoComponent)
  extends BaseMappingRepository("agent-mapping-agent-code", "agentCode", AgentCodeMapping.apply)
  with RepositoryFunctions[AgentCodeMapping]

case class VatAgentReferenceMapping(arn: String, vrn: String) extends ArnToIdentifierMapping {
  override def identifier: String = vrn
}

object VatAgentReferenceMapping extends ReactiveMongoFormats {
  implicit val formats: Format[VatAgentReferenceMapping] = format[VatAgentReferenceMapping]
}

@Singleton
class VatAgentReferenceMappingRepository @Inject() (implicit mongoComponent: ReactiveMongoComponent)
  extends BaseMappingRepository("agent-mapping-vat", "vrn", VatAgentReferenceMapping.apply)
  with RepositoryFunctions[VatAgentReferenceMapping]

