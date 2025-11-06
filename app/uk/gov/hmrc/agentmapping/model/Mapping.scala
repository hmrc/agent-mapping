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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.Json.format
import play.api.libs.json._
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypterDecrypter
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter

case class AgentReferenceMappings(mappings: Seq[AgentReferenceMapping])

object AgentReferenceMappings {
  implicit val formats: Format[AgentReferenceMappings] = format[AgentReferenceMappings]
}

case class AgentReferenceMapping(
  arn: Arn,
  identifier: String
)

object AgentReferenceMapping {

  implicit val writes: Writes[AgentReferenceMapping] = Json.writes[AgentReferenceMapping]

  implicit val reads: Reads[AgentReferenceMapping] = Json.reads[AgentReferenceMapping]

  implicit val formats: Format[AgentReferenceMapping] = Format(reads, writes)

  def databaseFormat(implicit
    crypto: Encrypter
      with Decrypter
  ): Format[AgentReferenceMapping] = {
    (
      (__ \ "arn").format[Arn] and
        (__ \ "identifier").format[String](stringEncrypterDecrypter)
    )(AgentReferenceMapping.apply, unlift(AgentReferenceMapping.unapply))
  }

}
