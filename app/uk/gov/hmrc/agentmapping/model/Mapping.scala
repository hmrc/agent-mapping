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
  def arn: String
  def identifier: String
}

case class AgentReferenceMapping(arn: String, identifier: String) extends ArnToIdentifierMapping

object AgentReferenceMapping extends ReactiveMongoFormats {
  implicit val formats: Format[AgentReferenceMapping] = format[AgentReferenceMapping]
}
