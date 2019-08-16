/*
 * Copyright 2019 HM Revenue & Customs
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

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import java.time.LocalDateTime
import play.api.libs.json.{JsPath, OFormat}
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.format

case class MappingDisplayRepositoryRecord(arn: Arn, mappings: Seq[MappingDisplayDetails])

object MappingDisplayRepositoryRecord {
  implicit val mappingDisplayRepositoryFormat: OFormat[MappingDisplayRepositoryRecord] = format
}

final case class MappingDisplayDetails(
  authProviderId: AuthProviderId,
  ggTag: GGTag,
  count: Int,
  createdOn: LocalDateTime)

object MappingDisplayDetails {

  import MongoLocalDateTimeFormat._

  implicit val mongoDisplayDetailsFormat: OFormat[MappingDisplayDetails] = (
    (JsPath \ "authProviderId").format[AuthProviderId] and
      (JsPath \ "ggTag").format[GGTag] and
      (JsPath \ "count").format[Int] and
      (JsPath \ "createdOn").format[LocalDateTime]
  )(MappingDisplayDetails.apply, unlift(MappingDisplayDetails.unapply))
}

final case class MappingDisplayRequest(authProviderId: AuthProviderId, ggTag: GGTag, count: Int)

object MappingDisplayRequest {
  implicit val mappingDisplayRequestFormat: OFormat[MappingDisplayRequest] = format
}
