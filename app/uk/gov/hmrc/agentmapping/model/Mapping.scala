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

package uk.gov.hmrc.agentmapping.model

import play.api.libs.json._
import play.api.libs.json.Json.format
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.domain.TaxIdentifier

import java.time.LocalDateTime

case class Mapping(
  arn: String,
  identifiers: Seq[Identifier]
)

object Mapping {
  implicit val formats: Format[Mapping] = format[Mapping]
}

case class AgentReferenceMappings(mappings: Seq[AgentReferenceMapping])

object AgentReferenceMappings {
  implicit val formats: Format[AgentReferenceMappings] = format[AgentReferenceMappings]
}

trait ArnToIdentifierMapping {

  def businessId: TaxIdentifier
  def identifier: String

}

case class AgentReferenceMapping(
  businessId: TaxIdentifier,
  identifier: String,
  createdDate: Option[LocalDateTime]
)
extends ArnToIdentifierMapping

object AgentReferenceMapping {

  import MongoLocalDateTimeFormat._

  implicit val writes: Writes[AgentReferenceMapping] =
    new Writes[AgentReferenceMapping] {
      override def writes(o: AgentReferenceMapping): JsValue =
        o match {
          case AgentReferenceMapping(
                Arn(arn),
                identifier,
                _
              ) =>
            Json.obj("arn" -> arn, "identifier" -> identifier)
          case AgentReferenceMapping(
                Utr(utr),
                identifier,
                Some(createdDate)
              ) =>
            Json.obj(
              "utr" -> utr,
              "identifier" -> identifier,
              "preCreatedDate" -> createdDate
            )
          case _ => throw new Exception(s"Unknown AgentReferenceMapping type: $o")
        }
    }

  implicit val reads: Reads[AgentReferenceMapping] =
    new Reads[AgentReferenceMapping] {
      override def reads(json: JsValue): JsResult[AgentReferenceMapping] =
        if ((json \ "arn").toOption.isDefined) {
          val arn = (json \ "arn").as[Arn]
          val identifier = (json \ "identifier").as[String]
          JsSuccess(AgentReferenceMapping(
            arn,
            identifier,
            None
          ))
        }
        else if ((json \ "utr").toOption.isDefined) {
          val utr = (json \ "utr").as[Utr]
          val identifier = (json \ "identifier").as[String]
          val preCreatedDate = (json \ "preCreatedDate").asOpt[LocalDateTime]
          JsSuccess(AgentReferenceMapping(
            utr,
            identifier,
            preCreatedDate
          ))
        }
        else
          JsError("invalid json")
    }

  implicit val formats: Format[AgentReferenceMapping] = Format(reads, writes)

}
