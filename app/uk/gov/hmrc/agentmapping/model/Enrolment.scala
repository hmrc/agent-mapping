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

package uk.gov.hmrc.agentmapping.model

import play.api.libs.json._

case class Enrolment(
  service: String,
  state: String,
  identifiers: Seq[EnrolmentIdentifier]
)

object Enrolment {
  implicit val format: Format[Enrolment] = Json.format[Enrolment]
}

case class EnrolmentIdentifier(
  key: String,
  value: String
) {
  override def toString: String = s"${key.toUpperCase}~${value.replace(" ", "")}"
}

object EnrolmentIdentifier {

  implicit val format: Format[EnrolmentIdentifier] = Json.format[EnrolmentIdentifier]
  implicit val ordering: Ordering[EnrolmentIdentifier] = Ordering.by(_.key)

}
