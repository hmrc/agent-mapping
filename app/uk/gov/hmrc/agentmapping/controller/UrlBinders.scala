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

package uk.gov.hmrc.agentmapping.controller

import play.api.mvc.PathBindable
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }

object UrlBinders {

  implicit val utrBinder = new PathBindable[Utr] {
    override def bind(key: String, utrValue: String): Either[String, Utr] = if (Utr.isValid(utrValue)) {
      Right(Utr(utrValue))
    } else {
      Left(raw""""$utrValue" is not a valid UTR""")
    }

    override def unbind(key: String, utr: Utr): String = utr.value
  }

  implicit val arnBinder = new PathBindable[Arn] {
    override def bind(key: String, arnValue: String): Either[String, Arn] = if (Arn.isValid(arnValue)) {
      Right(Arn(arnValue))
    } else {
      Left(raw""""$arnValue" is not a valid ARN""")
    }

    override def unbind(key: String, arn: Arn): String = arn.value
  }

}
