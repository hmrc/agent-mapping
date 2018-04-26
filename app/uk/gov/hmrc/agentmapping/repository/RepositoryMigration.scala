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
import org.joda.time
import org.joda.time.{ DateTimeZone, LocalDateTime }
import play.api.libs.json.{ JsObject, Json }
import play.api.{ Configuration, Logger }
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.ReadPreference
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.lock.{ LockKeeper, LockRepository }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.localDateTimeFormats

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SARepositoryMigration @Inject() (
  configuration: Configuration,
  fromRepository: SaAgentReferenceMappingRepository,
  toRepository: IRSAAGENTMappingRepository,
  mongoComponent: ReactiveMongoComponent,
  tryLock: MigrationLockService)(implicit ec: ExecutionContext)
  extends RepositoryMigration(configuration, fromRepository, toRepository, mongoComponent, tryLock)

@Singleton
class AgentCodeRepositoryMigration @Inject() (
  configuration: Configuration,
  fromRepository: AgentCodeMappingRepository,
  toRepository: NewAgentCodeMappingRepository,
  mongoComponent: ReactiveMongoComponent,
  tryLock: MigrationLockService)(implicit ec: ExecutionContext)
  extends RepositoryMigration(configuration, fromRepository, toRepository, mongoComponent, tryLock)

@Singleton
class VATRepositoryMigration @Inject() (
  configuration: Configuration,
  fromRepository: VatAgentReferenceMappingRepository,
  toRepository: HMCEVATAGNTMappingRepository,
  mongoComponent: ReactiveMongoComponent,
  tryLock: MigrationLockService)(implicit ec: ExecutionContext)
  extends RepositoryMigration(configuration, fromRepository, toRepository, mongoComponent, tryLock)

abstract class RepositoryMigration[A <: ArnToIdentifierMapping](
  configuration: Configuration,
  fromRepository: ReactiveRepository[A, BSONObjectID],
  toRepository: ReactiveRepository[AgentReferenceMapping, BSONObjectID],
  mongoComponent: ReactiveMongoComponent,
  tryLock: MigrationLockService)(implicit ec: ExecutionContext) {

  import ImplicitBSONHandlers._

  val logger = Logger(this.getClass)
  val migration: JSONCollection = mongoComponent.mongoConnector.db().collection[JSONCollection]("agent-mapping-migration-status")
  val id = s"from${fromRepository.getClass.getSimpleName}To${toRepository.getClass.getSimpleName}"

  def start(): Future[Option[Int]] = {

    migration
      .find(Json.obj("id" -> id))
      .one[JsObject](ReadPreference.primaryPreferred)
      .flatMap {
        case Some(obj) =>
          val timestamp = (obj \ "timestamp").as[LocalDateTime]
          logger.info(s"Migration $id already done at $timestamp, skipping.")
          Future.successful(None)
        case None =>
          progressMigration()
      }
  }

  private def progressMigration(): Future[Option[Int]] = {

    logger.info(s"Starting migration $id ...")

    tryLock(id) {
      val result = for {
        ic <- fromRepository.count
        mappings <- fromRepository.findAll()
        newMappings = mappings.map(m => AgentReferenceMapping(m.arn, m.identifier))
        _ <- toRepository.bulkInsert(newMappings)
        fc <- toRepository.count
        _ <- migration.insert(Json.obj("id" -> id, "ic" -> ic, "fc" -> fc, "timestamp" -> LocalDateTime.now(DateTimeZone.UTC)))
      } yield (ic, fc)
      result.transform(
        {
          case (ic, fc) =>
            logger.info(s"Migration $id is done, initial=$ic, final=$fc")
            fc
        },
        ex => {
          logger.error(s"Migration $id have failed", ex)
          ex
        })
    }

  }

  if (configuration.getBoolean("migrate-repositories").getOrElse(false)) {
    start()
  } else {
    logger.info("Migration of repositories disabled.")
  }
}

@Singleton
class MongoLockRepository @Inject() (implicit mongoComponent: ReactiveMongoComponent)
  extends LockRepository()(mongoComponent.mongoConnector.db)

@Singleton
class MigrationLockService @Inject() (lockRepository: LockRepository) {
  def apply[T](id: String)(body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
    new LockKeeper {
      override def repo = lockRepository
      override def lockId: String = id
      override val forceLockReleaseAfter: time.Duration = time.Duration.standardMinutes(5)
    }.tryLock(body)
}
