/*
 * Copyright 2026 HM Revenue & Customs
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

enum LegacyAgentEnrolment(val key: String):

  case IRAgentReference
  extends LegacyAgentEnrolment("IR-SA-AGENT")
  case AgentRefNo
  extends LegacyAgentEnrolment("HMCE-VAT-AGNT")
  case AgentCharId
  extends LegacyAgentEnrolment("HMRC-CHAR-AGENT")
  case HmrcGtsAgentRef
  extends LegacyAgentEnrolment("HMRC-GTS-AGNT")
  case HmrcMgdAgentRef
  extends LegacyAgentEnrolment("HMRC-MGD-AGNT")
  case VATAgentRefNo
  extends LegacyAgentEnrolment("HMRC-NOVRN-AGNT")
  case IRAgentReferenceCt
  extends LegacyAgentEnrolment("IR-CT-AGENT")
  case IRAgentReferencePaye
  extends LegacyAgentEnrolment("IR-PAYE-AGENT")
  case SdltStorn
  extends LegacyAgentEnrolment("IR-SDLT-AGENT")
  case AgentCode
  extends LegacyAgentEnrolment("AgentCode")

  def getDataBaseKey: String =

    this match
      case AgentCode => "agentcode"
      case _ => this.key.split("-")(1).toLowerCase
    end match

  end getDataBaseKey

end LegacyAgentEnrolment

object LegacyAgentEnrolment:

  def findByName(name: String): Option[LegacyAgentEnrolment] =

    name match
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
    end match

  end findByName

  def findByDataBaseKey(dbKey: String): Option[LegacyAgentEnrolment] =

    dbKey match
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
    end match

  end findByDataBaseKey

  implicit val format: Format[LegacyAgentEnrolment] =
    new Format[LegacyAgentEnrolment]:

      def reads(json: JsValue): JsResult[LegacyAgentEnrolment] =

        json match
          case JsString(s) =>

            findByName(s) match
              case Some(x) => JsSuccess(x)
              case None => JsError(s"Unexpected enrolment type: ${json.toString}")
            end match

          case _ => JsError(s"Enrolment type is not a string: $json")
        end match

      end reads

      def writes(o: LegacyAgentEnrolment): JsValue = JsString(o.key)

end LegacyAgentEnrolment
