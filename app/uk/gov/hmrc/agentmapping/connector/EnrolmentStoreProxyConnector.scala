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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json.format
import play.api.libs.json.{Json, OFormat, Writes}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector.responseHandler
import uk.gov.hmrc.agentmapping.util._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class Enrolment(state: String)

object Enrolment {
  implicit val formats: OFormat[Enrolment] = format
}

case class EnrolmentResponse(enrolments: Seq[Enrolment])

@Singleton
class EnrolmentStoreProxyConnector @Inject() (appConfig: AppConfig, http: HttpClient, metrics: Metrics)(implicit
  ec: ExecutionContext
) extends HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val batchSize = appConfig.clientCountBatchSize
  private val maxClientRelationships = appConfig.clientCountMaxResults

  private val espBaseUrl = appConfig.enrolmentStoreProxyBaseUrl

  private val IR_SA = "IR-SA"

  def getClientCount(userId: String, cumulativeCount: Int = 0, startRecord: Int = 1)(implicit
    hc: HeaderCarrier
  ): Future[Int] =
    cumulativeCount match {
      case cCount if cCount >= maxClientRelationships => maxClientRelationships
      case _ =>
        getDelegatedEnrolmentsCountFor(userId, startRecord, IR_SA).flatMap { case (prefilteredCount, filteredCount) =>
          if (prefilteredCount < batchSize) {
            filteredCount + cumulativeCount
          } else {
            getClientCount(userId, filteredCount + cumulativeCount, startRecord + batchSize)
          }
        }
    }

  // ES2 - delegated
  private def getDelegatedEnrolmentsCountFor(userId: String, startRecord: Int, service: String)(implicit
    hc: HeaderCarrier
  ): Future[(Int, Int)] = {
    def url(userId: String, startRecord: Int, service: String): String =
      s"$espBaseUrl/enrolment-store-proxy/enrolment-store/users/$userId/enrolments" +
        s"?type=delegated&service=$service&start-record=$startRecord&max-records=$batchSize"

    monitor(s"ConsumedAPI-ESPes2-$service-GET") {
      http.GET[EnrolmentResponse](url(userId, startRecord, service))(responseHandler, hc, ec).map { enrolmentResponse =>
        val filteredCount =
          enrolmentResponse.enrolments
            .count(e => e.state.toLowerCase == "activated" || e.state.toLowerCase == "unknown")

        (enrolmentResponse.enrolments.length, filteredCount)
      }
    }
  }
}

object EnrolmentStoreProxyConnector {
  implicit val responseHandler: HttpReads[EnrolmentResponse] = new HttpReads[EnrolmentResponse] {
    override def read(method: String, url: String, response: HttpResponse): EnrolmentResponse =
      Try(response.status match {
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
