/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.Inject
import play.api.libs.json.JsValue
import play.api.mvc.{Action, Request}
import uk.gov.hmrc.agentmapping.model.{MappingDisplayDetails, MappingDisplayRepositoryRecord, MappingDisplayRequest}
import uk.gov.hmrc.agentmapping.repository.MappingDisplayRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext

class MappingDetailsController @Inject()(repository: MappingDisplayRepository) extends BaseController {

//  def createOrUpdateMappingDisplayDetails(arn: Arn)(implicit ec: ExecutionContext): Action[JsValue] = Action.async(parse.json) {
//    implicit request: Request[JsValue] =>
//    withJsonBody[MappingDisplayDetails] { mappingDisplayRequest =>
//      repository.findByArn(arn).flatMap {
//        case Some(record) => {
//          val newRecord = record.copy(mappings = record.mappings ++ mappingDisplayRequest)
//          repository.upsert(arn, newRecord)
//        case None => ???
//      }}
//
//    }
//
//  }
//
//  def findMappingDisplayDetailsByArn(arn: Arn) = {
//
//  }

}
