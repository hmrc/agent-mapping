/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.Results
import uk.gov.hmrc.play.http.ws.WSHttpResponse
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Http {

  def get(url: String)(implicit hc: HeaderCarrier, client: WSClient): HttpResponse = perform(url) { request =>
    request.get()
  }

  def post(url: String, body: String, headers: Seq[(String, String)] = Seq.empty)
             (implicit hc: HeaderCarrier, client: WSClient): HttpResponse = perform(url) { request =>
    request.withHeaders(headers: _*).post(body)
  }

  def postEmpty(url: String)(implicit hc: HeaderCarrier, client: WSClient): HttpResponse = perform(url) { request =>
    import play.api.http.Writeable._
    request.post(Results.EmptyContent())
  }

  def putEmpty(url: String)(implicit hc: HeaderCarrier, client: WSClient): HttpResponse = perform(url) { request =>
    import play.api.http.Writeable._
    request.put(Results.EmptyContent())
  }

  def delete(url: String)(implicit hc: HeaderCarrier, client: WSClient): HttpResponse = perform(url) { request =>
    request.delete()
  }

  private def perform(url: String)(fun: WSRequest => Future[WSResponse])(implicit hc: HeaderCarrier, client: WSClient): WSHttpResponse =
    await(fun(client.url(url).withHeaders(hc.headers: _*).withRequestTimeout(20 seconds)).map(new WSHttpResponse(_)))

  private def await[A](future: Future[A]) = Await.result(future, 20 seconds)

}

class Resource(path: String, port: Int) {

  private def url() = s"http://localhost:$port$path"

  def get()(implicit hc: HeaderCarrier = HeaderCarrier(), client: WSClient): HttpResponse = Http.get(url)(hc, client)

  def delete()(implicit hc: HeaderCarrier = HeaderCarrier(), client: WSClient): HttpResponse = Http.delete(url)(hc, client)

  def postAsJson(body: String)(implicit hc: HeaderCarrier = HeaderCarrier(), client: WSClient): HttpResponse =
    Http.post(url, body, Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON))(hc, client)

  def postEmpty()(implicit hc: HeaderCarrier = HeaderCarrier(), client: WSClient): HttpResponse =
    Http.postEmpty(url)(hc, client)

  def putEmpty()(implicit hc: HeaderCarrier = HeaderCarrier(), client: WSClient): HttpResponse =
    Http.putEmpty(url)(hc, client)
}
