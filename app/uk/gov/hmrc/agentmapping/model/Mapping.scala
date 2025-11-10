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

import org.bson.types.ObjectId
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json._
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypterDecrypter
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

case class AgentReferenceMappings(mappings: Seq[AgentReferenceMapping])

object AgentReferenceMappings {
  implicit def apiWrites(identifierKey: String = "identifier"): Writes[AgentReferenceMappings] = {
    implicit val mappingWrites: Writes[AgentReferenceMapping] = AgentReferenceMapping.apiWrites(identifierKey)
    Json.writes[AgentReferenceMappings]
  }
}

case class AgentReferenceMapping(
  id: Option[ObjectId],
  arn: Arn,
  identifier: String
)

object AgentReferenceMapping {

  implicit def apiWrites(identifierKey: String = "identifier"): Writes[AgentReferenceMapping] =
    (
      (__ \ "arn").write[Arn] and
        (__ \ identifierKey).write[String] and
        (__ \ "created").writeNullable[LocalDate]
    )(mapping =>
      (
        mapping.arn,
        mapping.identifier,
        mapping.id.map(mongoId => Instant.ofEpochSecond(mongoId.getTimestamp).atZone(ZoneId.of("Europe/London")).toLocalDate)
      )
    )

  def databaseFormat(implicit
    crypto: Encrypter
      with Decrypter
  ): Format[AgentReferenceMapping] = {
    val databaseWrites: Writes[AgentReferenceMapping] =
      ( // Not writing _id as MongoDB creates this automatically
        (__ \ "arn").write[Arn] and
          (__ \ "identifier").write[String](stringEncrypterDecrypter)
      )(mapping => (mapping.arn, mapping.identifier))

    val databaseReads: Reads[AgentReferenceMapping] =
      ( // defaulting _id to None if it is missing or invalid because database is old (unknown if this can happen in practice)
        (__ \ "_id").readNullable[ObjectId](MongoFormats.objectIdFormat).orElse(Reads.pure(None)) and
          (__ \ "arn").read[Arn] and
          (__ \ "identifier").read[String](stringEncrypterDecrypter)
      )(AgentReferenceMapping.apply _)

    Format(databaseReads, databaseWrites)
  }

}
