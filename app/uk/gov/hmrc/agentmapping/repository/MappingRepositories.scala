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

package uk.gov.hmrc.agentmapping.repository

import uk.gov.hmrc.agentmapping.model._

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

//DO NOT DELETE (even if this microservice gets decommissioned)
@Singleton
class MappingRepositories @Inject() (
  agentCodeMappingRepository: NewAgentCodeMappingRepository,
  hMCEVATAGNTMappingRepository: HMCEVATAGNTMappingRepository,
  iRSAAGENTMappingRepository: IRSAAGENTMappingRepository,
  hMRCCHARAGENTMappingRepository: HMRCCHARAGENTMappingRepository,
  hMRCGTSAGNTMappingRepository: HMRCGTSAGNTMappingRepository,
  hMRCMGDAGNTMappingRepository: HMRCMGDAGNTMappingRepository,
  hMRCNOVRNAGNTMappingRepository: HMRCNOVRNAGNTMappingRepository,
  iRCTAGENTMappingRepository: IRCTAGENTMappingRepository,
  iRPAYEAGENTMappingRepository: IRPAYEAGENTMappingRepository,
  iRSDLTAGENTMappingRepository: IRSDLTAGENTMappingRepository
):

  private val repositories: Map[LegacyAgentEnrolment, MappingRepository] = Map(
    LegacyAgentEnrolment.AgentCode -> agentCodeMappingRepository,
    LegacyAgentEnrolment.IRAgentReference -> iRSAAGENTMappingRepository,
    LegacyAgentEnrolment.AgentRefNo -> hMCEVATAGNTMappingRepository,
    LegacyAgentEnrolment.AgentCharId -> hMRCCHARAGENTMappingRepository,
    LegacyAgentEnrolment.HmrcGtsAgentRef -> hMRCGTSAGNTMappingRepository,
    LegacyAgentEnrolment.HmrcMgdAgentRef -> hMRCMGDAGNTMappingRepository,
    LegacyAgentEnrolment.VATAgentRefNo -> hMRCNOVRNAGNTMappingRepository,
    LegacyAgentEnrolment.IRAgentReferenceCt -> iRCTAGENTMappingRepository,
    LegacyAgentEnrolment.IRAgentReferencePaye -> iRPAYEAGENTMappingRepository,
    LegacyAgentEnrolment.SdltStorn -> iRSDLTAGENTMappingRepository
  )

  def get(legacyAgentEnrolmentType: LegacyAgentEnrolment): MappingRepository =
    repositories(legacyAgentEnrolmentType)

  def map[T](f: MappingRepository => T): Seq[T] =
    repositories.values.map(f).toSeq

  def deleteDataForArn(arn: Arn)(implicit ec: ExecutionContext): Future[Seq[Int]] =
    Future.sequence(repositories.map { case (_, repository) => repository.deleteByArn(arn).map(dr => dr.getDeletedCount.toInt) }.toSeq)

end MappingRepositories
