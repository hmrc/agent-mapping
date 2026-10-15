/*
 * Copyright 2026 HM Revenue & Customs
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

import play.api.http.Status
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.util.RequestSupport.hc
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AgentServicesAccountConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2
)(
  implicit val ec: ExecutionContext
):

  private val agentServicesAccountBaseUrl = appConfig.agentServicesAccountBaseUrl

  def isSuspended(arn: Arn)(implicit rh: RequestHeader): Future[Boolean] = http
    .get(url"$agentServicesAccountBaseUrl/agent-services-account/agent-record-with-checks/arn/${arn.value}")
    .execute[HttpResponse]
    .map: response =>
      response.status match
        case Status.OK => (response.json \ "suspensionDetails" \ "suspensionStatus").asOpt[Boolean].getOrElse(false)
        case other =>
          throw UpstreamErrorResponse(
            response.body,
            other,
            other
          )

end AgentServicesAccountConnector
