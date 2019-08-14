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

package uk.gov.hmrc.agentmapping.connector

import java.net.URL

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Named, Singleton}
import play.api.libs.json.Json.format
import play.api.libs.json.{Json, OFormat, Writes}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector.responseHandler
import uk.gov.hmrc.agentmapping.util._
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet, HttpReads, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class Enrolment(state: String)

object Enrolment {
  implicit val formats: OFormat[Enrolment] = format
}

case class EnrolmentResponse(enrolments: Seq[Enrolment])

@Singleton
class EnrolmentStoreProxyConnector @Inject()(
  @Named("enrolment-store-proxy-baseUrl") baseUrl: URL,
  @Named("clientCount.maxRecords") batchSize: Int,
  http: HttpGet)(implicit metrics: Metrics, ec: ExecutionContext)
    extends HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getClientCount(userId: String)(implicit hc: HeaderCarrier): Future[Int] = {

    val vatCountF = getClientCount(userId, "HMCE-VATDEC-ORG")
    val irsaCountF = getClientCount(userId, "IR-SA")

    for {
      vatCount  <- vatCountF
      irService <- irsaCountF
    } yield vatCount + irService
  }

  private def getClientCount(userId: String, service: String)(implicit hc: HeaderCarrier): Future[Int] = {

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    def doGet(cumCount: Int = 0, startRecord: Int = 1): Future[Int] =
      getDelegatedEnrolmentsCountFor(userId, startRecord, service)
        .flatMap {
          case (preFilteredCount, batchCount) =>
            if (preFilteredCount < batchSize) {
              batchCount + cumCount
            } else
              doGet(batchCount + cumCount, startRecord + batchCount)
        }

    doGet()
  }

  //ES2 - delegated
  private def getDelegatedEnrolmentsCountFor(userId: String, startRecord: Int, service: String)(
    implicit hc: HeaderCarrier): Future[(Int, Int)] =
    monitor(s"ConsumedAPI-ESPes2-$service-GET") {

      def url(userId: String, startRecord: Int, service: String): URL =
        new URL(
          baseUrl,
          s"enrolment-store-proxy/enrolment-store/users/$userId/enrolments?type=delegated&service=$service&start-record=$startRecord&max-records=$batchSize"
        )

      http.GET[EnrolmentResponse](url(userId, startRecord, service).toString)(responseHandler, hc, ec).map {
        enrolmentResponse =>
          val filteredCount =
            enrolmentResponse.enrolments
              .count(e => e.state.toLowerCase == "activated" || e.state.toLowerCase == "unknown")

          (enrolmentResponse.enrolments.length, filteredCount)
      }
    }
}

object EnrolmentStoreProxyConnector {
  implicit val responseHandler: HttpReads[EnrolmentResponse] = new HttpReads[EnrolmentResponse] {
    override def read(method: String, url: String, response: HttpResponse): EnrolmentResponse =
      Try(response.status match {
        case 200 => EnrolmentResponse((response.json \ "enrolments").as[Seq[Enrolment]])
        case 204 => EnrolmentResponse(Seq.empty)
      }).getOrElse(throw new RuntimeException(
        s"Error retrieving client list from $url: status: ${response.status}, body: ${response.body}"))
  }

  implicit val writes: Writes[EnrolmentResponse] = Json.writes[EnrolmentResponse]
}
