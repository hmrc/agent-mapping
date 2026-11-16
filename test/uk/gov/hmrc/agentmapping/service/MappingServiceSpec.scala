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

package uk.gov.hmrc.agentmapping.service

import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmapping.audit.AuditService
import uk.gov.hmrc.agentmapping.connector.AgentServicesAccountConnector
import uk.gov.hmrc.agentmapping.connector.EnrolmentResponse
import uk.gov.hmrc.agentmapping.connector.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentmapping.model.*
import uk.gov.hmrc.agentmapping.repository.MappingRepository
import uk.gov.hmrc.agentmapping.repository.MappingRepositories

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MappingServiceSpec
extends AnyWordSpecLike
with BeforeAndAfterEach
with Matchers
with ScalaFutures
with MockitoSugar:

  private val auditService = mock[AuditService]
  private val enrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]
  private val agentServicesAccountConnector = mock[AgentServicesAccountConnector]
  private val repositories = mock[MappingRepositories]
  private val repository = mock[MappingRepository]
  private val service =
    new MappingService(
      auditService,
      enrolmentStoreProxyConnector,
      agentServicesAccountConnector,
      repositories
    )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    reset(
      auditService,
      enrolmentStoreProxyConnector,
      agentServicesAccountConnector,
      repositories,
      repository
    )

  "getActiveIdentifiers" should:
    "return activated enrolments with recognised service types" in:
      val groupId = "group-id"
      given Request[Any] = FakeRequest("GET", "/groups/group-id/enrolments")
      val expectedIdentifier = ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, "agent-code")

      when(enrolmentStoreProxyConnector.getPrincipalEnrolments(groupId)).thenReturn(Future.successful(
        EnrolmentResponse(Seq(
          Enrolment(
            LegacyAgentEnrolmentType.AgentCode.key,
            "aCtIvAtEd",
            Seq(EnrolmentIdentifier("AgentCode", "agent-code"))
          ),
          Enrolment(
            LegacyAgentEnrolmentType.AgentCode.key,
            "Delegated",
            Seq(EnrolmentIdentifier("AgentCode", "inactive-agent-code"))
          ),
          Enrolment(
            "UNKNOWN-SERVICE",
            "Activated",
            Seq(EnrolmentIdentifier("Unknown", "unknown-agent-code"))
          )
        ))
      ))

      service.getActiveIdentifiers(groupId).futureValue shouldBe Set(expectedIdentifier)

  "getActiveIdentifiers" should:
    "return all identifiers for activated enrolments with recognised service types" in:
      val groupId = "group-id"
      given Request[Any] = FakeRequest("GET", "/groups/group-id/enrolments")

      when(enrolmentStoreProxyConnector.getPrincipalEnrolments(groupId)).thenReturn(Future.successful(
        EnrolmentResponse(Seq(
          Enrolment(
            LegacyAgentEnrolmentType.AgentCode.key,
            "Activated",
            Seq(
              EnrolmentIdentifier("AgentCode", "agent-code-1"),
              EnrolmentIdentifier("AgentCode", "agent-code-2")
            )
          ),
          Enrolment(
            LegacyAgentEnrolmentType.AgentCode.key,
            "Pending",
            Seq(EnrolmentIdentifier("AgentCode", "inactive-agent-code"))
          ),
          Enrolment(
            "UNKNOWN-SERVICE",
            "Activated",
            Seq(EnrolmentIdentifier("Unknown", "unknown-agent-code"))
          )
        ))
      ))

      service.getActiveIdentifiers(groupId).futureValue shouldBe Set(
        ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, "agent-code-1"),
        ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, "agent-code-2")
      )

  "extractServiceIdentifiers" should:
    "extract identifiers for recognized enrolments" in:
      val enrolments = Seq(
        Enrolment(
          LegacyAgentEnrolmentType.AgentCode.key,
          "Activated",
          Seq(EnrolmentIdentifier("AgentCode", "agent-code"))
        )
      )

      MappingService.extractServiceIdentifiers(enrolments) shouldBe Set(
        ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, "agent-code")
      )

    "deduplicate matching identifiers" in:
      val enrolment = Enrolment(
        LegacyAgentEnrolmentType.AgentCode.key,
        "Activated",
        Seq(
          EnrolmentIdentifier("AgentCode", "agent-code"),
          EnrolmentIdentifier("AgentCode", "agent-code")
        )
      )

      MappingService.extractServiceIdentifiers(Seq(enrolment)) shouldBe Set(
        ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, "agent-code")
      )

  "createMappingInRepository" should:
    "audit a newly created mapping" in:
      val arn = Arn("AARN0000247")
      val identifier = ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, "agent-code")
      val ggCredId = "gg-cred-id"

      given Request[Any] = FakeRequest("POST", "/mapping")

      when(repositories.get(identifier.enrolmentType)).thenReturn(repository)
      when(repository.store(arn, identifier.value)).thenReturn(Future.successful(false))
      service
        .createMappings(
          arn,
          Set(identifier),
          isAutomapping = false,
          ggCredId
        )
        .futureValue shouldBe false

      verify(repository).store(
        arn,
        identifier.value
      )

      verify(auditService).sendCreateMappingAuditEvent(
        arn,
        identifier,
        ggCredId,
        duplicate = false,
        automapped = false
      )

      verifyNoMoreInteractions(auditService)

  "findMappedArnsAndAttemptAutomapping" should:
    "return existing mappings without attempting automapping" in:
      val identifier = ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, "agent-code")
      val arns = Seq(Arn("AARN0000247"), Arn("AARN0000254"))
      given Request[Any] = FakeRequest("GET", "/mapping")

      when(repositories.getAllArnsByServiceIdentifier(identifier)).thenReturn(Future.successful(arns))

      service.findMappedArnsAndAttemptAutomapping(
        identifier,
        "group-id",
        "cred-id"
      ).futureValue shouldBe arns.toSet

    "automap when there is exactly one ARN for another active identifier" in:
      val identifier = ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, "agent-code")
      val otherIdentifier = ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, "other-agent-code")
      val arn = Arn("AARN0000247")
      val groupId = "group-id"
      val credId = "cred-id"
      given Request[Any] = FakeRequest("GET", "/mapping")

      when(repositories.getAllArnsByServiceIdentifier(identifier)).thenReturn(Future.successful(Seq.empty))
      when(enrolmentStoreProxyConnector.getPrincipalEnrolments(groupId)).thenReturn(Future.successful(
        EnrolmentResponse(Seq(
          Enrolment(
            LegacyAgentEnrolmentType.AgentCode.key,
            "Activated",
            Seq(EnrolmentIdentifier("AgentCode", identifier.value))
          ),
          Enrolment(
            LegacyAgentEnrolmentType.AgentCode.key,
            "Activated",
            Seq(EnrolmentIdentifier("AgentCode", otherIdentifier.value))
          )
        ))
      ))
      when(repositories.getAllArnsByServiceIdentifier(otherIdentifier)).thenReturn(Future.successful(Seq(arn)))
      when(repositories.get(identifier.enrolmentType)).thenReturn(repository)
      when(repository.store(
        arn,
        identifier.value,
        automapped = true
      )).thenReturn(Future.successful(false))

      service.findMappedArnsAndAttemptAutomapping(
        identifier,
        groupId,
        credId
      ).futureValue shouldBe Set(arn)

      verify(repository).store(
        arn,
        identifier.value,
        automapped = true
      )
      verify(auditService).sendCreateMappingAuditEvent(
        arn,
        identifier,
        credId,
        duplicate = false,
        automapped = true
      )

    "not automap when other active identifiers map to multiple ARNs" in:
      val identifier = ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, "agent-code")
      val otherIdentifier = ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, "other-agent-code")
      val arns = Seq(Arn("AARN0000247"), Arn("AARN0000254"))
      val groupId = "group-id"
      given Request[Any] = FakeRequest("GET", "/mapping")

      when(repositories.getAllArnsByServiceIdentifier(identifier)).thenReturn(Future.successful(Seq.empty))
      when(enrolmentStoreProxyConnector.getPrincipalEnrolments(groupId)).thenReturn(Future.successful(
        EnrolmentResponse(Seq(
          Enrolment(
            LegacyAgentEnrolmentType.AgentCode.key,
            "Activated",
            Seq(EnrolmentIdentifier("AgentCode", identifier.value))
          ),
          Enrolment(
            LegacyAgentEnrolmentType.AgentCode.key,
            "Activated",
            Seq(EnrolmentIdentifier("AgentCode", otherIdentifier.value))
          )
        ))
      ))
      when(repositories.getAllArnsByServiceIdentifier(otherIdentifier)).thenReturn(Future.successful(arns))

      service.findMappedArnsAndAttemptAutomapping(
        identifier,
        groupId,
        "cred-id"
      ).futureValue shouldBe Set.empty

    "return no ARNs when there are no other active identifier mappings" in:
      val identifier = ServiceIdentifier(LegacyAgentEnrolmentType.AgentCode, "agent-code")
      val groupId = "group-id"
      given Request[Any] = FakeRequest("GET", "/mapping")

      when(repositories.getAllArnsByServiceIdentifier(identifier)).thenReturn(Future.successful(Seq.empty))
      when(enrolmentStoreProxyConnector.getPrincipalEnrolments(groupId)).thenReturn(Future.successful(
        EnrolmentResponse(Seq(
          Enrolment(
            LegacyAgentEnrolmentType.AgentCode.key,
            "Activated",
            Seq(EnrolmentIdentifier("AgentCode", identifier.value))
          )
        ))
      ))

      service.findMappedArnsAndAttemptAutomapping(
        identifier,
        groupId,
        "cred-id"
      ).futureValue shouldBe Set.empty

  "checkSuspensions" should:
    "return true when any ARN is suspended" in:
      val suspendedArn = Arn("AARN0000247")
      val activeArn = Arn("AARN0000254")
      given Request[Any] = FakeRequest("GET", "/mapping")

      when(agentServicesAccountConnector.isSuspended(suspendedArn)).thenReturn(Future.successful(true))
      when(agentServicesAccountConnector.isSuspended(activeArn)).thenReturn(Future.successful(false))

      service.checkSuspensions(Set(suspendedArn, activeArn)).futureValue shouldBe true

    "return false when no ARN is suspended" in:
      val arn = Arn("AARN0000247")
      given Request[Any] = FakeRequest("GET", "/mapping")

      when(agentServicesAccountConnector.isSuspended(arn)).thenReturn(Future.successful(false))

      service.checkSuspensions(Set(arn)).futureValue shouldBe false
