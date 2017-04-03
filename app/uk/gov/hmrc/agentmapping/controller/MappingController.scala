/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentmapping.controller

import javax.inject.{Inject, Singleton}

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Action
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.repository.MappingRepository
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.play.microservice.controller.BaseController

@Singleton
class MappingController @Inject()(mappingRepository: MappingRepository)  extends BaseController {

  def createMapping(arn: Arn, saAgentReference: SaAgentReference) = Action.async {
    mappingRepository.createMapping(arn, saAgentReference).map(_ => Created)
      .recover({
        case e: DatabaseException if e.getMessage().contains("E11000") => Conflict
      })
  }

}
