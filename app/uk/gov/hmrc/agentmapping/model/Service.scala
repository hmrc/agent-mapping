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
  val AgentCode, `IR-SA-AGENT`, `HMCE-VAT-AGNT`, `HMRC-CHAR-AGENT`, `HMRC-GTS-AGNT`, `HMRC-MGD-AGNT`, `HMRC-NOVRN-AGNT`, `IR-CT-AGENT`, `IR-PAYE-AGENT`, `IR-SDLT-AGENT` = Value

  private val names: Map[Name, String] = Map(
    `IR-SA-AGENT` -> "IR-SA-AGENT",
    `HMCE-VAT-AGNT` -> "HMCE-VAT-AGNT",
    `HMRC-CHAR-AGENT` -> "HMRC-CHAR-AGENT",
    `HMRC-GTS-AGNT` -> "HMRC-GTS-AGNT",
    `HMRC-MGD-AGNT` -> "HMRC-MGD-AGNT",
    `HMRC-NOVRN-AGNT` -> "HMRC-NOVRN-AGNT",
    `IR-CT-AGENT` -> "IR-CT-AGENT",
    `IR-PAYE-AGENT` -> "IR-PAYE-AGENT",
    `IR-SDLT-AGENT` -> "IR-SDLT-AGENT",
    `AgentCode` -> "AgentCode")

  private val reversedNames: Map[String, Name] = names.map { case (k, v) => (v, k) }
  require(names.size == reversedNames.size)

  val valueOf: String => Option[Name] = reversedNames.get

  implicit def asString(service: Name): String = names.getOrElse(service, "undefined")

  private val keys: Map[Name, String] = Map(
    `IR-SA-AGENT` -> "sa",
    `HMCE-VAT-AGNT` -> "vat",
    `HMRC-CHAR-AGENT` -> "char",
    `HMRC-GTS-AGNT` -> "gts",
    `HMRC-MGD-AGNT` -> "mgd",
    `HMRC-NOVRN-AGNT` -> "novrn",
    `IR-CT-AGENT` -> "ct",
    `IR-PAYE-AGENT` -> "paye",
    `IR-SDLT-AGENT` -> "sdlt",
    `AgentCode` -> "agentcode")

  private val keysReversed = keys.map { case (k, v) => (v, k) }
  require(keys.size == keysReversed.size)

  val keyFor: Name => String = keys.apply
  val forKey: String => Option[Name] = keysReversed.get

}
