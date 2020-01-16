/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.agentmapping.config

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject()(servicesConfig: ServicesConfig) {

  val appName = "agent-mapping"

  def getConf(key: String) = servicesConfig.getString(key)

  val clientCountMaxResults = servicesConfig.getInt("clientCount.maxRecords")
  val clientCountBatchSize = servicesConfig.getInt("clientCount.batchSize")

  val authBaseUrl = servicesConfig.baseUrl("auth")

  val enrolmentStoreProxyBaseUrl = servicesConfig.baseUrl("enrolment-store-proxy")

  val agentSubscriptionBaseUrl = servicesConfig.baseUrl("agent-subscription")

  val terminationStrideRole: String = servicesConfig.getString("termination.stride.enrolment")

}
