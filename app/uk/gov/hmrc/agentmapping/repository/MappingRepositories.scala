/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.mongo.MongoComponent

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MappingRepositories @Inject()(
  agentCodeMappingRepository: NewAgentCodeMappingRepository,
  hMCEVATAGNTMappingRepository: HMCEVATAGNTMappingRepository,
  iRSAAGENTMappingRepository: IRSAAGENTMappingRepository,
  hMRCCHARAGENTMappingRepository: HMRCCHARAGENTMappingRepository,
  hMRCGTSAGNTMappingRepository: HMRCGTSAGNTMappingRepository,
  hMRCMGDAGNTMappingRepository: HMRCMGDAGNTMappingRepository,
  hMRCNOVRNAGNTMappingRepository: HMRCNOVRNAGNTMappingRepository,
  iRCTAGENTMappingRepository: IRCTAGENTMappingRepository,
  iRPAYEAGENTMappingRepository: IRPAYEAGENTMappingRepository,
  iRSDLTAGENTMappingRepository: IRSDLTAGENTMappingRepository) {

  type Repository = MappingRepository with RepositoryFunctions[AgentReferenceMapping]

  private val repositories: Map[LegacyAgentEnrolmentType, Repository] =
    Map(
      AgentCode            -> agentCodeMappingRepository,
      IRAgentReference     -> iRSAAGENTMappingRepository,
      AgentRefNo           -> hMCEVATAGNTMappingRepository,
      AgentCharId          -> hMRCCHARAGENTMappingRepository,
      HmrcGtsAgentRef      -> hMRCGTSAGNTMappingRepository,
      HmrcMgdAgentRef      -> hMRCMGDAGNTMappingRepository,
      VATAgentRefNo        -> hMRCNOVRNAGNTMappingRepository,
      IRAgentReferenceCt   -> iRCTAGENTMappingRepository,
      IRAgentReferencePaye -> iRPAYEAGENTMappingRepository,
      SdltStorn            -> iRSDLTAGENTMappingRepository
    )

  def get(legacyAgentEnrolmentType: LegacyAgentEnrolmentType): Repository = repositories(legacyAgentEnrolmentType)

  def map[T](f: Repository => T): Seq[T] = repositories.values.map(f).toSeq

  def updateUtrToArn(arn: Arn, utr: Utr)(implicit ec: ExecutionContext): Future[Unit] =
    Future
      .sequence(repositories.map {
        case (_, repository) =>
          repository
            .updateUtrToArn(utr, arn)
      })
      .map(_ => ())
}

@Singleton
class IRSAAGENTMappingRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends NewMappingRepository("IR-SA-AGENT", mongo)

@Singleton
class NewAgentCodeMappingRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends NewMappingRepository("AgentCode", mongo)

@Singleton
class HMCEVATAGNTMappingRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends NewMappingRepository("HMCE-VAT-AGNT", mongo)

@Singleton
class HMRCCHARAGENTMappingRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends NewMappingRepository("HMRC-CHAR-AGENT", mongo)

@Singleton
class HMRCGTSAGNTMappingRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends NewMappingRepository("HMRC-GTS-AGNT", mongo)

@Singleton
class HMRCMGDAGNTMappingRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends NewMappingRepository("HMRC-MGD-AGNT", mongo)

@Singleton
class HMRCNOVRNAGNTMappingRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends NewMappingRepository("HMRC-NOVRN-AGNT", mongo)

@Singleton
class IRCTAGENTMappingRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends NewMappingRepository("IR-CT-AGENT", mongo)

@Singleton
class IRPAYEAGENTMappingRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends NewMappingRepository("IR-PAYE-AGENT", mongo)

@Singleton
class IRSDLTAGENTMappingRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends NewMappingRepository("IR-SDLT-AGENT", mongo)
