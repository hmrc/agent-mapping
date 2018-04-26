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

package uk.gov.hmrc.agentmapping.model

object Service extends Enumeration {
  type Name = Value
  val AgentCode, `IR-SA-AGENT`, `HMCE-VAT-AGNT` = Value

  private val names: Map[Service.Value, String] = Map(
    `IR-SA-AGENT` -> "IR-SA-AGENT",
    `HMCE-VAT-AGNT` -> "HMCE-VAT-AGNT",
    `AgentCode` -> "AgentCode")

  private val reverse: Map[String, Service.Value] = names.map { case (k, v) => (v, k) }

  val valueOf: String => Option[Service.Name] = reverse.get

  implicit def asString(service: Service.Name): String = names.getOrElse(service, "")
}
