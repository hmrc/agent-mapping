package uk.gov.hmrc.agentmapping.repository

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.play.json.ImplicitBSONHandlers
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.agentmapping.support.MongoApp
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class RepositoryMigrationISpec extends UnitSpec with MongoApp {

  import ImplicitBSONHandlers._

  override implicit lazy val app: Application = appBuilder.build()
  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(Map("migrate-repositories" -> "false"))
      .configure(mongoConfiguration)

  val oldSaRepo = app.injector.instanceOf[SaAgentReferenceMappingRepository]
  val newSaRepo = app.injector.instanceOf[IRSAAGENTMappingRepository]
  val oldAcRepo = app.injector.instanceOf[AgentCodeMappingRepository]
  val newAcRepo = app.injector.instanceOf[NewAgentCodeMappingRepository]
  val oldVatRepo = app.injector.instanceOf[VatAgentReferenceMappingRepository]
  val newVatRepo = app.injector.instanceOf[HMCEVATAGNTMappingRepository]

  "RepositoryMigration" should {

    await(oldSaRepo.ensureIndexes)
    await(newSaRepo.ensureIndexes)
    await(oldAcRepo.ensureIndexes)
    await(newAcRepo.ensureIndexes)
    await(oldVatRepo.ensureIndexes)
    await(newVatRepo.ensureIndexes)

    behave like migrateRepository(app.injector.instanceOf[SARepositoryMigration], "agent-mapping", "agent-mapping-ir-sa-agent", "saAgentReference")
    behave like migrateRepository(app.injector.instanceOf[AgentCodeRepositoryMigration], "agent-mapping-agent-code", "agent-mapping-agentcode", "agentCode")
    behave like migrateRepository(app.injector.instanceOf[VATRepositoryMigration], "agent-mapping-vat", "agent-mapping-hmce-vat-agnt", "vrn")
  }

  def migrateRepository(migration: RepositoryMigration[_], oldCollection: String, newCollection: String, oldIdentifierKey: String) = {
    s"migrate $oldCollection to $newCollection" in {

      val mongo = app.injector.instanceOf[ReactiveMongoComponent]
      val fromCollection = mongo.mongoConnector.db().collection[JSONCollection](oldCollection)

      val docs = Stream.range(0, 1000).map(i => Json.obj("arn" -> s"ARN0$i", oldIdentifierKey -> s"${oldIdentifierKey}9$i"))
      await(fromCollection.bulkInsert(docs, ordered = false))

      val result = await(migration.start())
      result shouldBe Some(1000)

      val newMappingCollection = mongo.mongoConnector.db().collection[JSONCollection](newCollection)

      265.until(936).by(127).foreach { i =>
        val mapping765 = await(newMappingCollection.find(Json.obj("arn" -> s"ARN0$i")).one[AgentReferenceMapping])
        mapping765 shouldBe Some(AgentReferenceMapping(s"ARN0$i", s"${oldIdentifierKey}9$i"))
      }

      (0 until 10).foreach { _ =>
        val nextMigrationResult = await(migration.start())
        nextMigrationResult shouldBe None
      }
    }
  }

}
