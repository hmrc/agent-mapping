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

package uk.gov.hmrc.agentmapping.connector

import java.net.URL
import javax.inject.{Inject, Named, Singleton}

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.Json.format
import play.api.libs.json.{Format, JsValue, Writes}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpPost, HttpReads}
import uk.gov.hmrc.http.logging.Authorization

object DesRegistrationRequest {
  implicit val formats: Format[DesRegistrationRequest] = format[DesRegistrationRequest]
}

case class DesRegistrationRequest(requiresNameMatch: Boolean = false, regime: String = "ITSA", isAnAgent: Boolean)

@Singleton
class DesConnector @Inject()(
  @Named("des.environment") environment: String,
  @Named("des.authorization-token") authorizationToken: String,
  @Named("des-baseUrl") baseUrl: URL,
  httpPost: HttpPost,
  metrics: Metrics)
    extends HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getArn(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Arn]] =
    getRegistrationJson(utr) map {
      case Some(r) => (r \ "agentReferenceNumber").asOpt[Arn]
      case _       => None
    }

  private def getRegistrationJson(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]] = {
    monitor(s"ConsumedAPI-DES-RegistrationIndividualUtr-POST") {
      (
        httpPost.POST[DesRegistrationRequest, Option[JsValue]](
          desRegistrationUrl(utr).toString,
          DesRegistrationRequest(isAnAgent = false))(
          implicitly[Writes[DesRegistrationRequest]],
          implicitly[HttpReads[Option[JsValue]]],
          desHeaders,
          ec
        )
      )
    }
  } recover {
    case badRequest: BadRequestException =>
      throw new RuntimeException(s"400 Bad Request response from DES for utr $utr", badRequest)
  }

  private def desRegistrationUrl(utr: Utr): URL =
    new URL(baseUrl, s"/registration/individual/utr/${encodePathSegment(utr.value)}")

  private def desHeaders(implicit hc: HeaderCarrier): HeaderCarrier =
    hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment)
}
