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

package uk.gov.hmrc.agentmapping.support

import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.agentmapping.model.AgentReferenceMapping
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolmentType
import uk.gov.hmrc.agentmapping.repository.MappingRepositories

/** Provides a way to create mappings for test. This will clear the mapping repositories before each test and includes method to populate the mapping
  * repositories with test data.
  */
trait MappingSupport
extends BeforeAndAfterEach:
  self: ServerBaseISpec =>

  private val mappingRepositories = app.injector.instanceOf[MappingRepositories]

  def populateMappings(testData: (LegacyAgentEnrolmentType, Seq[AgentReferenceMapping])*): Unit =
    for
      (enrolmentType, mappings) <- testData
      mapping <- mappings
    do
      mappingRepositories.get(enrolmentType).store(mapping.arn, mapping.identifier).futureValue

  override def beforeEach(): Unit =
    super.beforeEach()
    mappingRepositories.map(_.deleteAll().futureValue)
