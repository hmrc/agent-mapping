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

package uk.gov.hmrc.agentmapping.controllers

import org.scalatest.AppendedClues.*
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolmentType
import uk.gov.hmrc.agentmapping.stubs.AuthStubs
import uk.gov.hmrc.agentmapping.support.MappingSupport
import uk.gov.hmrc.agentmapping.support.ServerBaseISpec
import MappingLookupControllerISpec.mappingsByEnrolmentType

class MappingLookupControllerISpec
extends ServerBaseISpec
with MappingSupport
with AuthStubs
with ScalaFutures:

  "Retrieving a mapping" when:
    "looking for a list of agent code to enrolment key mappings" should:
      "return existing mappings" in:
        isLoggedIn

        populateMappings(mappingsByEnrolmentType.toSeq*)

        val response = callGet("/mappings/arn/JARN1234567")
        response.status shouldBe 200 withClue response.json
        response.json shouldBe Json.parse(
          """{
            |  "sdlt": [ "AAA0008" ],
            |  "mgd": [ "737B.89" ],
            |  "sa": [ "A1111A", "A1111B" ],
            |  "ct": [ "B2121C" ],
            |  "vat": [ "101747696" ],
            |  "char": [ "FGH7996KUJ" ],
            |  "paye": [ "F9876J" ]
            |}""".stripMargin
        )

      "not return new agent code mappings" in:
        isLoggedIn

        populateMappings(
          LegacyAgentEnrolmentType.AgentCode -> Seq(AgentReferenceMapping(
            id = None,
            arn = Arn("JARN1234567"),
            identifier = "AGENT123"
          ))
        )

        val response = callGet("/mappings/arn/JARN1234567")
        response.status shouldBe 200 withClue response.json
        response.json should not be Json.parse("""{"agentcode":["AGENT123"]}""")

      "returns an empty list when the mapping does not exist" in:
        isLoggedIn

        val response = callGet("/mappings/arn/JARN1234567")
        response.status shouldBe 200 withClue response.json
        response.json shouldBe Json.obj()

      "rejects unauthorised requests" in:
        givenUserNotAuthorisedWithError("MissingBearerToken")

        val response = callGet("/mappings/arn/JARN1234567")

        response.status shouldBe 401

    "looking up a ARN for the agent code and enrolment key" should:
      "return a matching ARNs for the agent code and enrolment key if they exist" in:
        isLoggedIn
        populateMappings(mappingsByEnrolmentType.toSeq*)

        for
          (enrolmentType, agentRefMappings) <- mappingsByEnrolmentType
          mapping <- agentRefMappings
        do
          val response = callGet(s"/mappings/key/${enrolmentType.enrolmentKey}/code/${mapping.identifier}")
          response.status shouldBe 200 withClue response.json

          // Build expected ARNs for this identifier and enrolment type from test data
          val expectedArns = agentRefMappings
            .filter(_.identifier == mapping.identifier)
            .map(_.arn.value)
            .distinct
            .sorted

          // Parse response and verify it matches expected ARNs
          val actualArns = response.json.as[Seq[String]].sorted
          actualArns shouldBe expectedArns withClue s"For ${enrolmentType.enrolmentKey}/${mapping.identifier}"

      "returns an empty list when the mapping does not exist" in:
        isLoggedIn

        val enrolmentKeys = LegacyAgentEnrolmentType.values.map(_.enrolmentKey).toSeq

        for enrolmentKey <- enrolmentKeys
        do
          val response = callGet(s"/mappings/key/$enrolmentKey/code/AGENT123")
          response.status shouldBe 200 withClue response.json
          response.json shouldBe JsArray.empty

      "rejects unauthorised requests" in:
        givenUserNotAuthorisedWithError("MissingBearerToken")

        for
          (enrolmentType, agentRefMappings) <- mappingsByEnrolmentType
          mapping <- agentRefMappings
        do
          val response = callGet(s"/mappings/key/${enrolmentType.enrolmentKey}/code/${mapping.identifier}")
          response.status shouldBe 401

  "looking up a ARN for the agent code" should:
    "return a matching ARNs for the agent code if they exist" in:
      isLoggedIn
      populateMappings(mappingsByEnrolmentType.toSeq*)

      for
        (_, agentRefMappings) <- mappingsByEnrolmentType
        mapping <- agentRefMappings
      do
        val response = callGet(s"/mappings/code/${mapping.identifier}")
        response.status shouldBe 200 withClue response.json
        // Response varies based on which ARNs are mapped to this identifier
        response.json.as[JsObject].keys should not be empty

    "returns an empty list when the mapping does not exist" in:
      isLoggedIn

      val response = callGet(s"/mappings/code/AGENT123")
      response.status shouldBe 200 withClue response.json
      response.json shouldBe JsObject.empty

    "rejects unauthorised requests" in:
      givenUserNotAuthorisedWithError("MissingBearerToken")

      for
        (_, agentRefMappings) <- mappingsByEnrolmentType
        mapping <- agentRefMappings
      do
        val response = callGet(s"/mappings/code/${mapping.identifier}")
        response.status shouldBe 401

object MappingLookupControllerISpec:

  private val mappingsByEnrolmentType: Map[LegacyAgentEnrolmentType, Seq[AgentReferenceMapping]] = Map(
    LegacyAgentEnrolmentType.SdltStorn -> Seq(
      AgentReferenceMapping(id = None, Arn("JARN1234567"), "AAA0008"),
      AgentReferenceMapping(id = None, Arn("JARN7654321"), "BBB0015"),
      AgentReferenceMapping(id = None, Arn("JARN1111111"), "CCC0022")
    ),
    LegacyAgentEnrolmentType.HmrcMgdAgentRef -> Seq(
      AgentReferenceMapping(id = None, Arn("JARN1234567"), "737B.89"),
      AgentReferenceMapping(id = None, Arn("JARN7654321"), "842C.90")
    ),
    LegacyAgentEnrolmentType.IRAgentReference -> Seq(
      AgentReferenceMapping(id = None, Arn("JARN1234567"), "A1111A"),
      AgentReferenceMapping(id = None, Arn("JARN1234567"), "A1111B"),
      AgentReferenceMapping(id = None, Arn("JARN1111111"), "A2222C")
    ),
    LegacyAgentEnrolmentType.HmrcGtsAgentRef -> Seq(
      AgentReferenceMapping(id = None, Arn("JARN7654321"), "AB8964622K"),
      AgentReferenceMapping(id = None, Arn("JARN1111111"), "CD1234567L")
    ),
    LegacyAgentEnrolmentType.IRAgentReferenceCt -> Seq(
      AgentReferenceMapping(id = None, Arn("JARN1234567"), "B2121C"),
      AgentReferenceMapping(id = None, Arn("JARN7654321"), "B3232D"),
      AgentReferenceMapping(id = None, Arn("JARN1111111"), "B4343E")
    ),
    LegacyAgentEnrolmentType.AgentRefNo -> Seq(
      AgentReferenceMapping(id = None, Arn("JARN1234567"), "101747696"),
      AgentReferenceMapping(id = None, Arn("JARN7654321"), "101747641")
    ),
    LegacyAgentEnrolmentType.VATAgentRefNo -> Seq(
      AgentReferenceMapping(id = None, Arn("JARN1111111"), "FGH7996KUJ"),
      AgentReferenceMapping(id = None, Arn("JARN1111111"), "JKL8097MNO")
    ),
    LegacyAgentEnrolmentType.AgentCharId -> Seq(
      AgentReferenceMapping(id = None, Arn("JARN1234567"), "FGH7996KUJ"),
      AgentReferenceMapping(id = None, Arn("JARN7654321"), "PQR8198STU")
    ),
    LegacyAgentEnrolmentType.IRAgentReferencePaye -> Seq(
      AgentReferenceMapping(id = None, Arn("JARN1234567"), "F9876J"),
      AgentReferenceMapping(id = None, Arn("JARN7654321"), "G8765K"),
      AgentReferenceMapping(id = None, Arn("JARN1111111"), "H7654L")
    )
  )
