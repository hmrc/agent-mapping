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

import org.joda.time.DateTime
import play.api.libs.json._
import play.api.libs.json.Json.format
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class Mapping(arn: String, identifiers: Seq[Identifier])

object Mapping extends ReactiveMongoFormats {
  implicit val formats: Format[Mapping] = format[Mapping]
}

case class AgentReferenceMappings(mappings: List[AgentReferenceMapping])

object AgentReferenceMappings extends ReactiveMongoFormats {
  implicit val formats: Format[AgentReferenceMappings] = format[AgentReferenceMappings]
}

trait ArnToIdentifierMapping {
  def businessId: TaxIdentifier
  def identifier: String
}

case class AgentReferenceMapping(businessId: TaxIdentifier, identifier: String, createdDate: Option[DateTime])
    extends ArnToIdentifierMapping

object AgentReferenceMapping extends ReactiveMongoFormats {
  implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats

  implicit val writes: Writes[AgentReferenceMapping] = new Writes[AgentReferenceMapping] {
    override def writes(o: AgentReferenceMapping): JsValue = o match {
      case AgentReferenceMapping(Arn(arn), identifier, _) =>
        Json.obj("arn" -> arn, "identifier" -> identifier)
      case AgentReferenceMapping(Utr(utr), identifier, createdDate) =>
        Json.obj("utr" -> utr, "identifier" -> identifier, "preCreatedDate" -> createdDate)
    }
  }

  implicit val reads: Reads[AgentReferenceMapping] = new Reads[AgentReferenceMapping] {
    override def reads(json: JsValue): JsResult[AgentReferenceMapping] =
      if ((json \ "arn").toOption.isDefined) {
        val arn = (json \ "arn").as[Arn]
        val identifier = (json \ "identifier").as[String]
        JsSuccess(AgentReferenceMapping(arn, identifier, None))
      } else if ((json \ "utr").toOption.isDefined) {
        val utr = (json \ "utr").as[Utr]
        val identifier = (json \ "identifier").as[String]
        val preCreatedDate = (json \ "preCreatedDate").as[DateTime]
        JsSuccess(AgentReferenceMapping(utr, identifier, Some(preCreatedDate)))
      } else JsError("invalid json")
  }

  implicit val formats: Format[AgentReferenceMapping] = Format(reads, writes)
}
