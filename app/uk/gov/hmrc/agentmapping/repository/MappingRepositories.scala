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

import org.apache.pekko.stream.Materializer
import uk.gov.hmrc.agentmapping.model._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.mongo.MongoComponent

import javax.inject.Inject
import javax.inject.Named
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
) {

  private val repositories: Map[LegacyAgentEnrolmentType, MappingRepository] = Map(
    AgentCode -> agentCodeMappingRepository,
    IRAgentReference -> iRSAAGENTMappingRepository,
    AgentRefNo -> hMCEVATAGNTMappingRepository,
    AgentCharId -> hMRCCHARAGENTMappingRepository,
    HmrcGtsAgentRef -> hMRCGTSAGNTMappingRepository,
    HmrcMgdAgentRef -> hMRCMGDAGNTMappingRepository,
    VATAgentRefNo -> hMRCNOVRNAGNTMappingRepository,
    IRAgentReferenceCt -> iRCTAGENTMappingRepository,
    IRAgentReferencePaye -> iRPAYEAGENTMappingRepository,
    SdltStorn -> iRSDLTAGENTMappingRepository
  )
  def get(legacyAgentEnrolmentType: LegacyAgentEnrolmentType): MappingRepository = repositories(legacyAgentEnrolmentType)

  def map[T](f: MappingRepository => T): Seq[T] = repositories.values.map(f).toSeq

  def deleteDataForArn(arn: Arn)(implicit ec: ExecutionContext): Future[Seq[Int]] = Future
    .sequence(repositories.map { case (_, repository) => repository.deleteByArn(arn).map(dr => dr.getDeletedCount.toInt) }.toSeq)

  def deleteDataForUtr(utr: Utr)(implicit ec: ExecutionContext): Future[Seq[Int]] = Future
    .sequence(repositories.map { case (_, repository) => repository.deleteByUtr(utr).map(dr => dr.getDeletedCount.toInt) }.toSeq)

  def updateUtrToArn(
    arn: Arn,
    utr: Utr
  )(implicit ec: ExecutionContext): Future[Unit] = Future
    .sequence(repositories.map { case (_, repository) =>
      repository
        .updateUtrToArn(utr, arn)
    })
    .map(_ => ())

}

@Singleton
class IRSAAGENTMappingRepository @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext,
  @Named("aes") crypto: Encrypter
    with Decrypter,
  mat: Materializer
)
extends MappingRepository(collectionName = "IR-SA-AGENT", mongo = mongoComponent)

@Singleton
class NewAgentCodeMappingRepository @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext,
  @Named("aes") crypto: Encrypter
    with Decrypter,
  mat: Materializer
)
extends MappingRepository(collectionName = "AgentCode", mongo = mongoComponent)

@Singleton
class HMCEVATAGNTMappingRepository @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext,
  @Named("aes") crypto: Encrypter
    with Decrypter,
  mat: Materializer
)
extends MappingRepository("HMCE-VAT-AGNT", mongo = mongoComponent)

@Singleton
class HMRCCHARAGENTMappingRepository @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext,
  @Named("aes") crypto: Encrypter
    with Decrypter,
  mat: Materializer
)
extends MappingRepository("HMRC-CHAR-AGENT", mongo = mongoComponent)

@Singleton
class HMRCGTSAGNTMappingRepository @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext,
  @Named("aes") crypto: Encrypter
    with Decrypter,
  mat: Materializer
)
extends MappingRepository("HMRC-GTS-AGNT", mongo = mongoComponent)

@Singleton
class HMRCMGDAGNTMappingRepository @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext,
  @Named("aes") crypto: Encrypter
    with Decrypter,
  mat: Materializer
)
extends MappingRepository("HMRC-MGD-AGNT", mongo = mongoComponent)

@Singleton
class HMRCNOVRNAGNTMappingRepository @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext,
  @Named("aes") crypto: Encrypter
    with Decrypter,
  mat: Materializer
)
extends MappingRepository("HMRC-NOVRN-AGNT", mongo = mongoComponent)

@Singleton
class IRCTAGENTMappingRepository @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext,
  @Named("aes") crypto: Encrypter
    with Decrypter,
  mat: Materializer
)
extends MappingRepository("IR-CT-AGENT", mongo = mongoComponent)

@Singleton
class IRPAYEAGENTMappingRepository @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext,
  @Named("aes") crypto: Encrypter
    with Decrypter,
  mat: Materializer
)
extends MappingRepository("IR-PAYE-AGENT", mongo = mongoComponent)

@Singleton
class IRSDLTAGENTMappingRepository @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext,
  @Named("aes") crypto: Encrypter
    with Decrypter,
  mat: Materializer
)
extends MappingRepository("IR-SDLT-AGENT", mongo = mongoComponent)
