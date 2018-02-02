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

import play.api.libs.json.Format
import play.api.libs.json.Json.format
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.http.BadRequestException
import Names._

case class Identifiers(get: Seq[Identifier]) {
  def isSingle: Boolean = get.size == 1
  override def toString: String = get.mkString("~")
}

case class Identifier(key: String, value: String) {

  key match {
    case VATRegNo =>
      if (!Vrn.isValid(value)) {
        throw new BadRequestException(s"Identifier validation failed for $this")
      }
    case _ =>
  }

  override def toString: String = s"$key~$value"
}

object Identifier {
  implicit val formats: Format[Identifier] = format[Identifier]
}

object Identifiers {

  def parse(arg: String): Identifiers = {
    val args = arg.split("~")
    if (args.size == 1) Identifiers(Seq(Identifier(IRAgentReference, args(0)))) //Backward compatibility with agent-mapping-frontend <= 0.26.0
    else {
      if (args.size % 2 != 0) throw new IllegalArgumentException("Identifier must be KEY~VALUE formatted or a sequence of such separated by ~")
      else {
        val identifiers: Seq[Identifier] = args.sliding(2, 2).map(a => Identifier(a(0), a(1))).toSeq
        Identifiers(identifiers)
      }
    }
  }
}

