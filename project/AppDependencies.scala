import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {

  private val bootstrapVer: String = "9.12.0"
  private val mongoVer: String = "2.6.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVer,
    "uk.gov.hmrc"       %% "agent-mtd-identifiers"     % "2.2.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % mongoVer,
    "com.typesafe.play" %% "play-json"                 % "2.9.4",
    "uk.gov.hmrc"       %% "crypto-json-play-30"       % "8.2.0"
  )
  
  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30" % mongoVer     % Test,
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootstrapVer % Test,
    "org.mockito"            %% "mockito-scala-scalatest" % "1.17.31"    % Test
  )

}
