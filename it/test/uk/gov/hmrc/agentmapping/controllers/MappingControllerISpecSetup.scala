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

package uk.gov.hmrc.agentmapping.controllers

import com.google.inject.AbstractModule
import org.apache.pekko.actor.ActorSystem
import org.mongodb.scala.result.DeleteResult
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentmapping.stubs.AuthStubs
import uk.gov.hmrc.agentmapping.stubs.DataStreamStub
import uk.gov.hmrc.agentmapping.stubs.EnrolmentStoreStubs
import uk.gov.hmrc.agentmapping.support.WireMockSupport
import uk.gov.hmrc.agentmapping.controller.MappingController
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolment
import uk.gov.hmrc.agentmapping.module.DuplicateArnScanModule
import uk.gov.hmrc.agentmapping.repository.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.concurrent.Future

trait MappingControllerISpecSetup
extends AnyWordSpecLike
with GuiceOneServerPerSuite
with Matchers
with OptionValues
with WireMockSupport
with AuthStubs
with DataStreamStub
with EnrolmentStoreStubs
with ScalaFutures
with MongoSupport:

  implicit val actorSystem: ActorSystem = ActorSystem()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .disable[DuplicateArnScanModule]
      .configure(
        Map(
          "microservice.services.auth.port" -> wireMockPort.toString,
          "microservice.services.enrolment-store-proxy.port" -> wireMockPort.toString,
          "auditing.consumer.baseUri.host" -> wireMockHost,
          "auditing.consumer.baseUri.port" -> wireMockPort.toString,
          "application.router" -> "testOnlyDoNotUseInAppConf.Routes",
          "migrate-repositories" -> "false",
          "termination.stride.enrolment" -> "caat",
          "mongodb.uri" -> mongoUri
        )
      )
      .overrides(new TestGuiceModule)
  end appBuilder

  override implicit lazy val app: Application = appBuilder.build()

  protected lazy val repositories: MappingRepositories = app.injector.instanceOf[MappingRepositories]

  lazy val controller: MappingController = app.injector.instanceOf[MappingController]

  lazy val saRepo: MappingRepository = repositories.get(LegacyAgentEnrolment.IRAgentReference)
  lazy val vatRepo: MappingRepository = repositories.get(LegacyAgentEnrolment.AgentRefNo)
  lazy val agentCodeRepo: MappingRepository = repositories.get(LegacyAgentEnrolment.AgentCode)

  private class TestGuiceModule
  extends AbstractModule:

    override def configure(): Unit =
      bind(classOf[MongoComponent]).toInstance(mongoComponent)

  end TestGuiceModule

  def deleteTestDataInAllCollections(): Seq[DeleteResult] = Await.result(Future.sequence(repositories.map(coll => coll.deleteAll())), 20.seconds)

  override def commonStubs(): Unit =
    givenAuditConnector
    ()
  end commonStubs

  override def beforeEach(): Unit =
    super.beforeEach()
    commonStubs()
    deleteTestDataInAllCollections()
    ()
  end beforeEach

  override def afterAll(): Unit =
    deleteTestDataInAllCollections()
    super.afterAll()
    ()
  end afterAll

end MappingControllerISpecSetup
