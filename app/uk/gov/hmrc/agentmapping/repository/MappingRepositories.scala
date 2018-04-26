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

import javax.inject.{ Inject, Singleton }
import uk.gov.hmrc.agentmapping.model.Service
import uk.gov.hmrc.agentmapping.model.Service._

@Singleton
class MappingRepositories @Inject() (
  hMCEVATAGNTMappingRepository: HMCEVATAGNTMappingRepository,
  iRSAAGENTMappingRepository: IRSAAGENTMappingRepository,
  agentCodeMappingRepository: NewAgentCodeMappingRepository) {

  type Repository = MappingRepository with RepositoryFunctions[AgentReferenceMapping]

  private val repositories: Map[Service.Name, Repository] =
    Map(
      `IR-SA-AGENT` -> iRSAAGENTMappingRepository,
      `HMCE-VAT-AGNT` -> hMCEVATAGNTMappingRepository,
      AgentCode -> agentCodeMappingRepository)

  def get(serviceName: Service.Name): Repository = repositories(serviceName)

  def map[T](f: Repository => T): Seq[T] = repositories.values.map(f).toSeq

}
