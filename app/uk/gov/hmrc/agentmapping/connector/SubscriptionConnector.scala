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

import play.api.libs.json.{JsLookupResult, Json}
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.model.{AuthProviderId, UserMapping}
import uk.gov.hmrc.agentmapping.util.HttpAPIMonitor
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionConnector @Inject() (appConfig: AppConfig, http: HttpClient, val metrics: Metrics)(implicit
  val ec: ExecutionContext
) extends HttpAPIMonitor {

  def getUserMappings(internalId: AuthProviderId)(implicit hc: HeaderCarrier): Future[Option[List[UserMapping]]] = {

    val url =
      s"${appConfig.agentSubscriptionBaseUrl}/agent-subscription/subscription/journey/id/${encodePathSegment(internalId.id)}"

    val timer = metrics.defaultRegistry.timer("ConsumedAPI-Agent-Subscription-getJourneyByPrimaryId-GET")
    timer.time()
    monitor("ConsumedAPI-Agent-Subscription-getJourneyByPrimaryId-GET") {
      http
        .GET[HttpResponse](url.toString)
        .map { response =>
          response.status match {
            case 200 =>
              val userMappings: JsLookupResult = Json.parse(response.body) \ "userMappings"
              Some(userMappings.as[List[UserMapping]])

            case 204 => None
          }
        }
    }
  }
}
