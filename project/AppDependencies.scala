import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {

  private val bootstrapVer: String = "10.7.0"
  private val mongoVer: String = "2.12.0"
  private val playFrameworkVersion = "play-30"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-backend-$playFrameworkVersion" % bootstrapVer,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playFrameworkVersion"        % mongoVer,
//    "org.playframework" %% "play-json"                                % "3.0.6",
    "uk.gov.hmrc"       %% s"crypto-json-$playFrameworkVersion"       % "8.4.0",
    "uk.gov.hmrc"       %% s"domain-$playFrameworkVersion"            % "11.0.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test-$playFrameworkVersion"  % bootstrapVer % Test,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-$playFrameworkVersion" % mongoVer     % Test,
//    "org.mockito"            %% "mockito-scala-scalatest"                % "2.1.0"      % Test
  )

}
