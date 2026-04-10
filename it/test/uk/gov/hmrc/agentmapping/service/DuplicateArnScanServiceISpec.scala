/*
 * Copyright 2025 HM Revenue & Customs
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

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.agentmapping.repository.NewAgentCodeMappingRepository
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.model.ArnCount
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class DuplicateArnScanServiceISpec
extends AnyWordSpec
with Matchers
with ScalaFutures
with BeforeAndAfterEach
with GuiceOneAppPerSuite:

  lazy val repository: NewAgentCodeMappingRepository = app.injector.instanceOf[NewAgentCodeMappingRepository]

  lazy val service: DuplicateArnScanService = app.injector.instanceOf[DuplicateArnScanService]

  override def beforeEach(): Unit =
    super.beforeEach()
    repository.deleteAll().futureValue
  end beforeEach

  "findIdentifierArnCounts" should {
    "return a row for each identifier that has more than one ARN" in {
      repository.store(Arn("ARN0001"), "X").futureValue
      repository.store(Arn("ARN0002"), "X").futureValue
      repository.store(Arn("ARN0003"), "Y").futureValue

      val results: Seq[ArnCount] =
        service
          .findIdentifierArnCounts(repository.collection)
          .futureValue

      results.map(_.arnCount) should contain only 2
    }
  }

end DuplicateArnScanServiceISpec
