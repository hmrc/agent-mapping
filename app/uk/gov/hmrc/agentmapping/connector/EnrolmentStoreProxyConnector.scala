/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.agentmapping.connector

import play.api.libs.json.Json.format
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.Writes
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector.responseHandler
import uk.gov.hmrc.agentmapping.util.RequestSupport.hc
import uk.gov.hmrc.agentmapping.util._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

case class Enrolment(state: String)

object Enrolment {
  implicit val formats: OFormat[Enrolment] = format
}

case class EnrolmentResponse(enrolments: Seq[Enrolment])

@Singleton
class EnrolmentStoreProxyConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2,
  val metrics: Metrics
)(implicit
  val ec: ExecutionContext
) {

  private val batchSize = appConfig.clientCountBatchSize
  private val maxClientRelationships = appConfig.clientCountMaxResults

  private val espBaseUrl = appConfig.enrolmentStoreProxyBaseUrl

  private val IR_SA = "IR-SA"

  def getClientCount(
    userId: String,
    cumulativeCount: Int = 0,
    startRecord: Int = 1
  )(implicit
    rh: RequestHeader
  ): Future[Int] =
    cumulativeCount match {
      case cCount if cCount >= maxClientRelationships => maxClientRelationships
      case _ =>
        getDelegatedEnrolmentsCountFor(
          userId,
          startRecord,
          IR_SA
        ).flatMap { case (prefilteredCount, filteredCount) =>
          if (prefilteredCount < batchSize) {
            filteredCount + cumulativeCount
          }
          else {
            getClientCount(
              userId,
              filteredCount + cumulativeCount,
              startRecord + batchSize
            )
          }
        }
    }

  // ES2 - delegated
  private def getDelegatedEnrolmentsCountFor(
    userId: String,
    startRecord: Int,
    service: String
  )(implicit
    rh: RequestHeader
  ): Future[(Int, Int)] = {
    def es2Url(
      userId: String,
      startRecord: Int,
      service: String
    ): URL =
      url"$espBaseUrl/enrolment-store-proxy/enrolment-store/users/$userId/enrolments?type=delegated&service=$service&start-record=$startRecord&max-records=$batchSize"

    http.get(es2Url(
      userId,
      startRecord,
      service
    )).execute[EnrolmentResponse].map { enrolmentResponse =>
      val filteredCount = enrolmentResponse.enrolments
        .count(e => e.state.toLowerCase == "activated" || e.state.toLowerCase == "unknown")

      (enrolmentResponse.enrolments.length, filteredCount)
    }

  }

}

object EnrolmentStoreProxyConnector {

  implicit val responseHandler: HttpReads[EnrolmentResponse] =
    new HttpReads[EnrolmentResponse] {
      override def read(
        method: String,
        url: String,
        response: HttpResponse
      ): EnrolmentResponse = Try(response.status match {
        case 200 => EnrolmentResponse((response.json \ "enrolments").as[Seq[Enrolment]])
        case 204 => EnrolmentResponse(Seq.empty)
      }).getOrElse(
        throw new RuntimeException(
          s"Error retrieving client list from $url: status: ${response.status}, body: ${response.body}"
        )
      )
    }

  implicit val writes: Writes[EnrolmentResponse] = Json.writes[EnrolmentResponse]

}
