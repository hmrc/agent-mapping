/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentmapping.service

import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Accumulators
import org.mongodb.scala.model.Aggregates
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Projections

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.Logging
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping
import uk.gov.hmrc.agentmapping.model.ArnCount
import uk.gov.hmrc.agentmapping.repository.MappingRepositories
import uk.gov.hmrc.mongo.lock.LockService
import uk.gov.hmrc.mongo.lock.MongoLockRepository

@Singleton
class DuplicateArnScanService @Inject() (
  mappingRepositories: MappingRepositories,
  mongoLockRepository: MongoLockRepository
)(implicit ec: ExecutionContext)
extends Logging {

  private val lockService = LockService(
    mongoLockRepository,
    lockId = "mapping-duplicate-arn-scan",
    ttl = 10.minutes
  )

  def runDuplicateArnScanLocked(): Future[Unit] = lockService.withLock {
    logger.warn("Acquired lock for mapping-duplicate-arn-scan; starting duplicate ARN analysis")
    runDuplicateArnScan()
  }.map {
    case Some(_) => logger.warn("Duplicate ARN scan completed; lock released")
    case None => logger.warn("Duplicate ARN scan skipped (another instance already running)")
  }

  private[service] def findIdentifierArnCounts(collection: MongoCollection[AgentReferenceMapping])(implicit ec: ExecutionContext): Future[Seq[ArnCount]] = {

    val pipeline = Seq(
      Aggregates.group(
        "$identifier",
        Accumulators.addToSet("arns", "$arn")
      ),
      Aggregates.project(
        Projections.fields(
          Projections.computed("identifier", "$_id"),
          Projections.computed("arnCount", Document("""{ "$size": "$arns" }"""))
        )
      ),
      Aggregates.`match`(Filters.gt("arnCount", 1))
    )

    collection
      .aggregate[Document](pipeline)
      .toFuture()
      .map(_.map { doc =>
        ArnCount(
          identifier = doc.getString("identifier"),
          arnCount = doc.getInteger("arnCount")
        )
      })
  }

  private def runDuplicateArnScan(): Future[Unit] = {
    val tasks = mappingRepositories.map { repo =>
      findIdentifierArnCounts(repo.collection).map { results =>
        val distribution: Map[Int, Int] =
          results
            .groupBy(_.arnCount)
            .view
            .mapValues(_.size)
            .toMap

        logger.warn(
          s"Duplicate ARN distribution in '${repo.collectionName}': " +
            (if (distribution.isEmpty)
               "no duplicates found"
             else
               distribution.map { case (arnCount, count) => s"$count identifiers have $arnCount ARNs" }.mkString(", "))
        )
      }
    }

    Future.sequence(tasks).map(_ => ())
  }

  runDuplicateArnScanLocked()

}
