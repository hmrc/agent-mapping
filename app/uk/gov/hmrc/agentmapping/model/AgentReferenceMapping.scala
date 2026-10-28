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
import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Correlates an ARN with an enrolment identifier (either a legacy or newly assigned agent code, depending on the originating repository).
  *
  * @param created
  *   when the mapping was created (either by a user or automatically by the system)
  * @param arn
  *   the agent reference number
  * @param identifier
  *   an agent code
  * @param automapped
  *   whether the system automatically created this mapping (true) or it was manually created by a user (false)
  */
case class AgentReferenceMapping(
  created: Instant,
  arn: Arn,
  identifier: String,
  automapped: Boolean = false
)

object AgentReferenceMapping:

  def apiWrites(identifierKey: String = "identifier"): Writes[AgentReferenceMapping] =
    (
      (__ \ "arn").write[Arn] and
        (__ \ identifierKey).write[String] and
        (__ \ "created").write[LocalDate]
    )(mapping =>
      (
        mapping.arn,
        mapping.identifier,
        mapping.created.atZone(ZoneId.of("Europe/London")).toLocalDate
      )
    )
  end apiWrites

  val databaseFormat: Format[AgentReferenceMapping] =
    val timestampFromObjectId = (__ \ "_id").read[ObjectId](using MongoFormats.objectIdFormat)
      .map(objectId => Instant.ofEpochSecond(objectId.getTimestamp))

    val createdOrObjectIdTimestamp = (__ \ "created").read[Instant](using MongoJavatimeFormats.instantReads)
      .orElse(timestampFromObjectId)

    val databaseWrites: Writes[AgentReferenceMapping] =
      (
        (__ \ "created").write[Instant](using MongoJavatimeFormats.instantWrites) and
          (__ \ "arn").write[Arn] and
          (__ \ "identifier").write[String] and
          (__ \ "automapped").write[Boolean]
      )(mapping =>
        (mapping.created, mapping.arn, mapping.identifier, mapping.automapped)
      )

    val databaseReads: Reads[AgentReferenceMapping] =
      (
        createdOrObjectIdTimestamp and
          (__ \ "arn").read[Arn] and
          (__ \ "identifier").read[String] and
          (__ \ "automapped").readNullable[Boolean].map(_.getOrElse(false))
      )(AgentReferenceMapping.apply)

    Format(databaseReads, databaseWrites)

  end databaseFormat

end AgentReferenceMapping
