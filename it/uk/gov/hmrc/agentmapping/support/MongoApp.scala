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

package uk.gov.hmrc.agentmapping.support

import org.scalatest.{BeforeAndAfterEach, Suite, TestSuite}
import org.scalatestplus.play.OneServerPerSuite
import uk.gov.hmrc.mongo.{MongoSpecSupport, Awaiting => MongoAwaiting}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

trait MongoApp extends MongoSpecSupport with ResetMongoBeforeTest with OneServerPerSuite {
  me: TestSuite =>

  protected def mongoConfiguration = Map("mongodb.uri" -> mongoUri)
}

trait ResetMongoBeforeTest extends BeforeAndAfterEach {
  me: Suite with MongoSpecSupport =>

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
  }

  def dropMongoDb()(implicit ec: ExecutionContext = global): Unit =
    Awaiting.await(mongo().drop())
}

object Awaiting extends MongoAwaiting
