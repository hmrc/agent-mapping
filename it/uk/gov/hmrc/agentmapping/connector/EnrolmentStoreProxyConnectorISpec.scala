package uk.gov.hmrc.agentmapping.connector

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.metrics.Metrics
import uk.gov.hmrc.agentmapping.stubs.EnrolmentStoreStubs
import uk.gov.hmrc.agentmapping.support.{BaseISpec, WireMockSupport}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global

class EnrolmentStoreProxyConnectorISpec  extends BaseISpec with WireMockSupport with GuiceOneAppPerSuite with EnrolmentStoreStubs{

  implicit val hc = HeaderCarrier()

  private lazy implicit val metrics = app.injector.instanceOf[Metrics]
  private lazy val http = app.injector.instanceOf[HttpClient]

  override implicit lazy val app: Application = appBuilder.build()

   val appConfig = app.injector.instanceOf[AppConfig]

  private lazy val connector: EnrolmentStoreProxyConnector =
    new EnrolmentStoreProxyConnector(appConfig, http, metrics)

  private val maxRecordsToDisplay = appConfig.clientCountMaxResults
  private val clientCountBatchSize = appConfig.clientCountBatchSize

  private val HMCE_VATDEC_ORG = "HMCE-VATDEC-ORG"
  private val IR_SA = "IR-SA"

  private def batchResponse(recordsToReturn: Int, active: Boolean = true) = {
    EnrolmentResponse(List.fill(recordsToReturn)(if(active) Enrolment("Activated") else Enrolment("NotActivated")))
  }

  "runEs2ForServices" should {
    s"return $maxRecordsToDisplay if the total records from $HMCE_VATDEC_ORG is higher than $maxRecordsToDisplay" in {

      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 1,  batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 8,  batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 15,  batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 22,  batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 29,  batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 36,  batchResponse(clientCountBatchSize), 200)
      await(connector.getClientCount("agent1")) shouldBe 40
    }

    s"return $maxRecordsToDisplay if the total records from $HMCE_VATDEC_ORG alone is lower than $maxRecordsToDisplay but not when combined with $IR_SA" in {

      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 1,  batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 8,  batchResponse(clientCountBatchSize - 2), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 1, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 8, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 15, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 22, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 29, batchResponse(clientCountBatchSize - 4), 200)

      await(connector.getClientCount("agent1")) shouldBe 40

    }

    s"return $maxRecordsToDisplay if the total records return from each $HMCE_VATDEC_ORG and $IR_SA is exactly half of the $maxRecordsToDisplay" in {

      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 1,  batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 8,  batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 15,  batchResponse(clientCountBatchSize - 1), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 1, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 8, batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 15, batchResponse(clientCountBatchSize -1), 200)

      await(connector.getClientCount("agent1")) shouldBe 40

    }

    s"return the actual number of records when the total from $HMCE_VATDEC_ORG is less than $maxRecordsToDisplay and there are no records from $IR_SA" in {

      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 1,  batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 8,  batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 15,  batchResponse(clientCountBatchSize - 1), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 1, batchResponse(0), 200)

      await(connector.getClientCount("agent1")) shouldBe 20
    }

    s"return the actual number of records when the total from $HMCE_VATDEC_ORG and $IR_SA is less than $maxRecordsToDisplay" in {

      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 1,  batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 8,  batchResponse(clientCountBatchSize), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 15,  batchResponse(clientCountBatchSize - 1), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 1, batchResponse(clientCountBatchSize - 2), 200)

      await(connector.getClientCount("agent1")) shouldBe 25
    }

    s"return the actual number of records when there are only records from $IR_SA and is less than $maxRecordsToDisplay" in {

      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 1,  batchResponse(0), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 1, batchResponse(clientCountBatchSize - 2), 200)

      await(connector.getClientCount("agent1")) shouldBe 5
    }

    s"return the count of only Activated records" in {

      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 1, batchResponse(clientCountBatchSize, false), 200)
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 8, batchResponse(clientCountBatchSize - 2), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 1, batchResponse(clientCountBatchSize, false), 200)
      givenEs2ClientsFoundFor("agent1", IR_SA, 8, batchResponse(clientCountBatchSize - 2), 200)

      await(connector.getClientCount("agent1")) shouldBe 10
    }

    "return 0 if the call to ESP returns 204" in {
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 1,  batchResponse(0), 204)
      givenEs2ClientsFoundFor("agent1",IR_SA, 1, batchResponse(0), 204)

      await(connector.getClientCount("agent1")) shouldBe 0
    }

    "throw an exception if the call to ESP does not work" in {
      givenEs2ClientsFoundFor("agent1", HMCE_VATDEC_ORG, 1,  batchResponse(0), 502)

      val exception = intercept[RuntimeException] {
        await(connector.getClientCount("agent1"))
      }
      exception.getMessage.contains("Error retrieving client list from") shouldBe true
    }

  }
}
