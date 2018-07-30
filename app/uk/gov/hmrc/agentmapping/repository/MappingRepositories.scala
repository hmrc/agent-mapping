/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmapping.model.Service._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}

import scala.concurrent.ExecutionContext

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

  private val repositories: Map[Service.Name, Repository] =
    Map(
      AgentCode         -> agentCodeMappingRepository,
      `IR-SA-AGENT`     -> iRSAAGENTMappingRepository,
      `HMCE-VAT-AGNT`   -> hMCEVATAGNTMappingRepository,
      `HMRC-CHAR-AGENT` -> hMRCCHARAGENTMappingRepository,
      `HMRC-GTS-AGNT`   -> hMRCGTSAGNTMappingRepository,
      `HMRC-MGD-AGNT`   -> hMRCMGDAGNTMappingRepository,
      `HMRC-NOVRN-AGNT` -> hMRCNOVRNAGNTMappingRepository,
      `IR-CT-AGENT`     -> iRCTAGENTMappingRepository,
      `IR-PAYE-AGENT`   -> iRPAYEAGENTMappingRepository,
      `IR-SDLT-AGENT`   -> iRSDLTAGENTMappingRepository
    )

  def get(serviceName: Service.Name): Repository = repositories(serviceName)

  def map[T](f: Repository => T): Seq[T] = repositories.values.map(f).toSeq

  def updateArn(arn: Arn, utr: Utr)(implicit ec: ExecutionContext) = repositories.foreach { case (_, repository) =>
    for {
      identifiers <- repository.findBy(utr)
      _ <- repository.delete(utr)
      _ <- identifiers.map(x => repository.store(arn, x.identifier))
    } yield ()
  }
}

@Singleton
class IRSAAGENTMappingRepository @Inject()(implicit mongoComponent: ReactiveMongoComponent)
    extends NewMappingRepository("IR-SA-AGENT")

@Singleton
class NewAgentCodeMappingRepository @Inject()(implicit mongoComponent: ReactiveMongoComponent)
    extends NewMappingRepository("AgentCode")

@Singleton
class HMCEVATAGNTMappingRepository @Inject()(implicit mongoComponent: ReactiveMongoComponent)
    extends NewMappingRepository("HMCE-VAT-AGNT")

@Singleton
class HMRCCHARAGENTMappingRepository @Inject()(implicit mongoComponent: ReactiveMongoComponent)
    extends NewMappingRepository("HMRC-CHAR-AGENT")

@Singleton
class HMRCGTSAGNTMappingRepository @Inject()(implicit mongoComponent: ReactiveMongoComponent)
    extends NewMappingRepository("HMRC-GTS-AGNT")

@Singleton
class HMRCMGDAGNTMappingRepository @Inject()(implicit mongoComponent: ReactiveMongoComponent)
    extends NewMappingRepository("HMRC-MGD-AGNT")

@Singleton
class HMRCNOVRNAGNTMappingRepository @Inject()(implicit mongoComponent: ReactiveMongoComponent)
    extends NewMappingRepository("HMRC-NOVRN-AGNT")

@Singleton
class IRCTAGENTMappingRepository @Inject()(implicit mongoComponent: ReactiveMongoComponent)
    extends NewMappingRepository("IR-CT-AGENT")

@Singleton
class IRPAYEAGENTMappingRepository @Inject()(implicit mongoComponent: ReactiveMongoComponent)
    extends NewMappingRepository("IR-PAYE-AGENT")

@Singleton
class IRSDLTAGENTMappingRepository @Inject()(implicit mongoComponent: ReactiveMongoComponent)
    extends NewMappingRepository("IR-SDLT-AGENT")
