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

import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue

/** A comprehensive list of all the old (pre-MTD) agent enrolment types IR-SA-AGENT is the only legacy code we actually use in authentication others are
  * captured for future use. One Mongo DB per instance of this type
  */
sealed abstract class LegacyAgentEnrolmentType {

  def key: String =
    this match {
      case IRAgentReference => "IR-SA-AGENT"
      case AgentRefNo => "HMCE-VAT-AGNT"
      case AgentCharId => "HMRC-CHAR-AGENT"
      case HmrcGtsAgentRef => "HMRC-GTS-AGNT"
      case HmrcMgdAgentRef => "HMRC-MGD-AGNT"
      case VATAgentRefNo => "HMRC-NOVRN-AGNT"
      case IRAgentReferenceCt => "IR-CT-AGENT"
      case IRAgentReferencePaye => "IR-PAYE-AGENT"
      case SdltStorn => "IR-SDLT-AGENT"
      case AgentCode => "AgentCode" // not an actual enrolment, but included here for completeness
    }

  def dbKey: String =
    this match {
      case AgentCode => "agentcode"
      case _ => this.key.split("-")(1).toLowerCase
    }

}

object LegacyAgentEnrolmentType {

  implicit val format: Format[LegacyAgentEnrolmentType] =
    new Format[LegacyAgentEnrolmentType] {

      def reads(json: JsValue): JsResult[LegacyAgentEnrolmentType] =
        json match {
          case JsString(s) =>
            find(s) match {
              case Some(x) => JsSuccess(x)
              case None => JsError(s"Unexpected enrolment type: ${json.toString}")
            }
          case _ => JsError(s"Enrolment type is not a string: $json")
        }

      def writes(o: LegacyAgentEnrolmentType): JsValue = JsString(o.key)
    }

  def find(key: String): Option[LegacyAgentEnrolmentType] =
    key match {
      case "IR-SA-AGENT" => Some(IRAgentReference)
      case "HMCE-VAT-AGNT" => Some(AgentRefNo)
      case "HMRC-CHAR-AGENT" => Some(AgentCharId)
      case "HMRC-GTS-AGNT" => Some(HmrcGtsAgentRef)
      case "HMRC-MGD-AGNT" => Some(HmrcMgdAgentRef)
      case "HMRC-NOVRN-AGNT" => Some(VATAgentRefNo)
      case "IR-CT-AGENT" => Some(IRAgentReferenceCt)
      case "IR-PAYE-AGENT" => Some(IRAgentReferencePaye)
      case "IR-SDLT-AGENT" => Some(SdltStorn)
      case "AgentCode" => Some(AgentCode)
      case _ => None
    }

  def findByDbKey(dbKey: String): Option[LegacyAgentEnrolmentType] =
    dbKey match {
      case "sa" => Some(IRAgentReference)
      case "vat" => Some(AgentRefNo)
      case "char" => Some(AgentCharId)
      case "gts" => Some(HmrcGtsAgentRef)
      case "mgd" => Some(HmrcMgdAgentRef)
      case "novrn" => Some(VATAgentRefNo)
      case "ct" => Some(IRAgentReferenceCt)
      case "paye" => Some(IRAgentReferencePaye)
      case "sdlt" => Some(SdltStorn)
      case "agentcode" => Some(AgentCode)
      case _ => None
    }

}

case object IRAgentReference
extends LegacyAgentEnrolmentType
case object AgentRefNo
extends LegacyAgentEnrolmentType
case object AgentCharId
extends LegacyAgentEnrolmentType
case object HmrcGtsAgentRef
extends LegacyAgentEnrolmentType
case object HmrcMgdAgentRef
extends LegacyAgentEnrolmentType
case object VATAgentRefNo
extends LegacyAgentEnrolmentType
case object IRAgentReferenceCt
extends LegacyAgentEnrolmentType
case object IRAgentReferencePaye
extends LegacyAgentEnrolmentType
case object SdltStorn
extends LegacyAgentEnrolmentType
case object AgentCode
extends LegacyAgentEnrolmentType
