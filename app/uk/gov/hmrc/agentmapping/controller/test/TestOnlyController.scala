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

package uk.gov.hmrc.agentmapping.controller.test

import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.agentmapping.model.Arn
import uk.gov.hmrc.agentmapping.repository.MappingRepositories
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class TestOnlyController @Inject() (
  repositories: MappingRepositories,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
extends BackendController(cc) {

  def delete(arn: Arn): Action[AnyContent] = Action.async {
    repositories.deleteDataForArn(arn).map { _ =>
      NoContent
    }
  }

}
