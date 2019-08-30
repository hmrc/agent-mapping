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

package uk.gov.hmrc.agentmapping.metrics

import com.codahale.metrics.{Counter, Timer}
import javax.inject.{Inject, Singleton}

@Singleton
class Metrics @Inject()(val metrics: com.kenshoo.play.metrics.Metrics) {

  def timer(name: String): Timer = metrics.defaultRegistry.timer(name)

  def counter(name: String): Counter = metrics.defaultRegistry.counter(name)

  def espDelegatedEnrolmentsCountTimer(service: String): Timer = timer(s"ConsumedAPI-ESPes2-$service-GET")

  val getUserMappingsTimer: Timer = timer("ConsumedAPI-Agent-Subscription-getJourneyByPrimaryId-GET")

}
