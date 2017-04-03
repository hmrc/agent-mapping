package uk.gov.hmrc.agentmapping

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSClient
import uk.gov.hmrc.agentmapping.repository.MappingRepository
import uk.gov.hmrc.agentmapping.support.{MongoApp, Resource}
import uk.gov.hmrc.play.test.UnitSpec
import scala.concurrent.ExecutionContext.Implicits.global

class MappingControllerISpec extends UnitSpec with MongoApp {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val client: WSClient = AhcWSClient()

  private val repo: MappingRepository = app.injector.instanceOf[MappingRepository]

  override def beforeEach() {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  "/agent-mapping/mappings/:arn/:saAgentRef" should {
    "return created" in {

      val response = new Resource("/agent-mapping/mappings/ARN1122/A1111A", port).putEmpty()

      response.status shouldBe 201
    }

    "return conflict when the mapping already exists" in {

      new Resource("/agent-mapping/mappings/ARN1122/A1111A", port).putEmpty()
      val response = new Resource("/agent-mapping/mappings/ARN1122/A1111A", port).putEmpty()

      response.status shouldBe 409
    }
  }
}
