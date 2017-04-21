/*
 * Copyright 2017 HM Revenue & Customs
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

import uk.gov.hmrc.domain.{SimpleObjectReads, SimpleObjectWrites, TaxIdentifier}

object Arn {
  private[model] val arnPattern = "^[A-Z]ARN[0-9]{7}$".r

  def isValid(utr: String): Boolean = utr match {
    case Arn.arnPattern(_*) => true
    case _ => false
  }

  implicit val arnReads = new SimpleObjectReads[Arn]("value", Arn.apply)
  implicit val arnWrites = new SimpleObjectWrites[Arn](_.value)
}

case class Arn(value: String) extends TaxIdentifier

