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

package uk.gov.hmrc.agentmapping.config

import javax.inject.Inject
import javax.inject.Singleton
import uk.gov.hmrc.agentmapping.model.BasicAuthentication
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject() (servicesConfig: ServicesConfig) {

  val appName = "agent-mapping"

  def getConf(key: String): String = servicesConfig.getString(key)

  val clientCountMaxResults: Int = servicesConfig.getInt("clientCount.maxRecords")
  val clientCountBatchSize: Int = servicesConfig.getInt("clientCount.batchSize")

  val authBaseUrl: String = servicesConfig.baseUrl("auth")

  val enrolmentStoreProxyBaseUrl: String = servicesConfig.baseUrl("enrolment-store-proxy")

  val agentSubscriptionBaseUrl: String = servicesConfig.baseUrl("agent-subscription")

  val terminationStrideRole: String = servicesConfig.getString("termination.stride.enrolment")

  def expectedAuth: BasicAuthentication = {
    val username = servicesConfig.getString("agent-termination.username")
    val password = servicesConfig.getString("agent-termination.password")

    BasicAuthentication(username, password)
  }

}
