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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json.format
import play.api.libs.json.{Json, OFormat, Writes}
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector.responseHandler
import uk.gov.hmrc.agentmapping.metrics.Metrics
import uk.gov.hmrc.agentmapping.util._
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class Enrolment(state: String)

object Enrolment {
  implicit val formats: OFormat[Enrolment] = format
}

case class EnrolmentResponse(enrolments: Seq[Enrolment])

@Singleton
class EnrolmentStoreProxyConnector @Inject()(appConfig: AppConfig, http: HttpClient, metrics: Metrics)(
  implicit ec: ExecutionContext) {

  private val batchSize = appConfig.clientCountBatchSize
  private val maxClientRelationships = appConfig.clientCountMaxResults

  private val espBaseUrl = appConfig.enrolmentStoreProxyBaseUrl

  private val HMCE_VATDEC_ORG = "HMCE-VATDEC-ORG"
  private val IR_SA = "IR-SA"

  def getClientCount(userId: String)(implicit hc: HeaderCarrier): Future[Int] =
    for {
      vatF  <- getClientCount(userId, HMCE_VATDEC_ORG)
      irSaF <- getClientCount(userId, IR_SA, vatF)
    } yield irSaF

  private def getClientCount(userId: String, service: String, cumulativeCount: Int = 0)(
    implicit hc: HeaderCarrier): Future[Int] = {

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    def doGet(cumCount: Int = cumulativeCount, startRecord: Int = 1): Future[Int] = cumCount match {
      case c if c >= maxClientRelationships => maxClientRelationships
      case _ =>
        getDelegatedEnrolmentsCountFor(userId, startRecord, service).flatMap {
          case (prefilteredCount, filteredCount) =>
            if (prefilteredCount < batchSize) {
              filteredCount + cumCount
            } else
              doGet(filteredCount + cumCount, startRecord + batchSize)
        }

    }

    doGet()
  }

  //ES2 - delegated
  private def getDelegatedEnrolmentsCountFor(userId: String, startRecord: Int, service: String)(
    implicit hc: HeaderCarrier): Future[(Int, Int)] = {

    def url(userId: String, startRecord: Int, service: String): String =
      s"$espBaseUrl/enrolment-store-proxy/enrolment-store/users/$userId/enrolments?type=delegated&service=$service&start-record=$startRecord&max-records=$batchSize"
    val timerContext = metrics.espDelegatedEnrolmentsCountTimer(service).time()
    http.GET[EnrolmentResponse](url(userId, startRecord, service).toString)(responseHandler, hc, ec).map {
      enrolmentResponse =>
        timerContext.stop()
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
