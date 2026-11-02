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

import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.documentToUntypedDocument
import org.mongodb.scala.model.Accumulators
import org.mongodb.scala.model.Aggregates
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Projections
import play.api.Logging
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.model.ArnCount
import uk.gov.hmrc.agentmapping.repository.IRSAAGENTMappingRepository
import uk.gov.hmrc.agentmapping.util.NoRequest
import uk.gov.hmrc.mongo.lock.LockService
import uk.gov.hmrc.mongo.lock.MongoLockRepository

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.jdk.CollectionConverters.*

@Singleton
class DuplicateArnScanService @Inject() (
  saMappingRepo: IRSAAGENTMappingRepository,
  mongoLockRepository: MongoLockRepository,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  appConfig: AppConfig
)(using
  ExecutionContext,
  Materializer
)
extends Logging:

  private val lockService = LockService(
    mongoLockRepository,
    lockId = "mapping-duplicate-arn-scan",
    ttl = 10.minutes
  )

  private def runDuplicateArnScanLocked(): Future[Unit] =
    lockService.withLock {
      logger.warn("Acquired lock for mapping-duplicate-arn-scan; starting duplicate ARN analysis")
      runDuplicateArnScan()
    }.map:
      case Some(_) => logger.warn("Duplicate ARN scan completed; lock released")
      case None => logger.warn("Duplicate ARN scan skipped (another instance already running)")
  end runDuplicateArnScanLocked

  private[service] def findIdentifierArnCounts(collection: MongoCollection[AgentReferenceMapping])(implicit ec: ExecutionContext): Future[Seq[ArnCount]] =

    val pipeline = Seq(
      Aggregates.group(
        "$identifier",
        Accumulators.addToSet("arns", "$arn")
      ),
      Aggregates.project(
        Projections.fields(
          Projections.computed("identifier", "$_id"),
          Projections.computed("arns", "$arns"),
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
          arns = doc.getList("arns", classOf[String]).asScala.toSeq,
          arnCount = doc.getInteger("arnCount")
        )
      })
  end findIdentifierArnCounts

  private def runDuplicateArnScan(): Future[Done] =
    findIdentifierArnCounts(saMappingRepo.collection).flatMap { results =>
      val distribution: Map[Int, Int] =
        results
          .groupBy(_.arnCount)
          .view
          .mapValues(_.size)
          .toMap

      logger.warn(
        s"[DuplicateArnScanService] Duplicate ARN distribution in '${saMappingRepo.collectionName}': " +
          (if distribution.isEmpty then
             "no duplicates found"
           else
             distribution.map { case (arnCount, count) => s"$count identifiers have $arnCount ARNs" }.mkString(", "))
      )
      groupIdAndClientLookup(results).map { groupIdMapping =>
        groupIdMapping.map:
          case (identifier, arnMapping) =>
            logger.warn(
              s"[DuplicateArnScanService] Identifier '$identifier' has ${arnMapping.size} ARNs: \n ${arnMapping.map { case (arn, hasGroupId, count) =>
                  s"$arn, has groupId: $hasGroupId, ITSA count: $count"
                }.mkString(",\n ")}"
            )
      }.map(_ => Done)
    }
  end runDuplicateArnScan

  private def groupIdAndClientLookup(arnCounts: Seq[ArnCount]): Future[Seq[(String, Seq[(String, Boolean, Int)])]] =
    Source(arnCounts)
      .mapAsync(1) {
        case ArnCount(
              identifier,
              arns,
              _
            ) =>
          Source(arns)
            .throttle(5, 1.second)
            .mapAsync(1) { arn =>
              enrolmentStoreProxyConnector.getGroupIdFor(Arn(arn))(using NoRequest)
                .flatMap {
                  case Some(groupId) =>
                    enrolmentStoreProxyConnector.countEnrolmentsForGroupId(
                      groupId,
                      "HMRC-MTD-IT"
                    )(using NoRequest).map(count => (arn, true, count))
                  case None => Future.successful((arn, false, 0))
                }
                .recover {
                  case _: UpstreamErrorResponse => (arn, true, 0) // Default to true if we can't get a response from ESP, to avoid false positives in the scan
                }
            }
            .runWith(Sink.seq)
            .map(arnMapping => (identifier, arnMapping))
      }
      .runWith(Sink.seq)

  if (appConfig.runDuplicateArnScan)
    runDuplicateArnScanLocked()

end DuplicateArnScanService
