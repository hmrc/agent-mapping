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

package uk.gov.hmrc.agentmapping.connector

import java.net.URL
import javax.inject.{Inject, Named, Singleton}

import play.api.libs.json.Json.format
import play.api.libs.json.{Format, JsValue, Writes}
import uk.gov.hmrc.domain.{SimpleObjectReads, SimpleObjectWrites}
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier, HttpPost, HttpReads}

import scala.concurrent.{ExecutionContext, Future}


object Arn {
  implicit val arnReads = new SimpleObjectReads[Arn]("arn", Arn.apply)
  implicit val arnWrites = new SimpleObjectWrites[Arn](_.arn)
}

object DesRegistrationRequest {
  implicit val formats: Format[DesRegistrationRequest] = format[DesRegistrationRequest]
}

case class Arn(arn: String)
case class DesRegistrationResponse( agentReferenceNumber: Option[Arn])
case class DesRegistrationRequest(requiresNameMatch: Boolean = false, regime: String = "ITSA", isAnAgent: Boolean)


@Singleton
class DesConnector @Inject() (@Named("des.environment") environment: String,
                              @Named("des.authorization-token") authorizationToken: String,
                              @Named("des-baseUrl") baseUrl: URL,
                              httpPost: HttpPost){
  def getRegistration(utr: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DesRegistrationResponse]] =
    getRegistrationJson(utr) map {
      case Some(r) => Some(DesRegistrationResponse((r \ "agentReferenceNumber").asOpt[Arn]))
      case _ => None
    }

  private def getRegistrationJson(utr: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]] = {
    (httpPost.POST[DesRegistrationRequest, Option[JsValue]](desRegistrationUrl(utr).toString, DesRegistrationRequest(isAnAgent = false))
      (implicitly[Writes[DesRegistrationRequest]], implicitly[HttpReads[Option[JsValue]]], desHeaders))
  } recover {
    case badRequest: BadRequestException =>
      throw new RuntimeException(s"400 Bad Request response from DES for utr $utr", badRequest)
  }

  private def desRegistrationUrl(utr: String): URL =
    new URL(baseUrl, s"/registration/individual/utr/${encodePathSegment(utr)}")

  private def desHeaders(implicit hc: HeaderCarrier): HeaderCarrier = {
    hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment)
  }
}
