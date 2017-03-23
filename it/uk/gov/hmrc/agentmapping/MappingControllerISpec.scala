package uk.gov.hmrc.agentmapping

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatestplus.play.OneServerPerSuite
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSClient
import uk.gov.hmrc.agentmapping.support.Resource
import uk.gov.hmrc.play.test.UnitSpec

class MappingControllerISpec extends UnitSpec with OneServerPerSuite {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val client: WSClient = AhcWSClient()

  "/agent-mapping/mappings/:arn/:saAgentRef" should {
    "return created" in {

      val response = new Resource("/agent-mapping/mappings/ARN1122/A1111A", port).putEmpty()

      response.status shouldBe 201
    }
  }
}
