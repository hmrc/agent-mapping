import sbt.Tests.{Group, SubProcess}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.SbtAutoBuildPlugin
import scalariform.formatter.preferences._

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimum := 80.00,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val compileDeps = Seq(
  "uk.gov.hmrc" %% "bootstrap-play-25" % "1.5.0",
  "uk.gov.hmrc" %% "auth-client" % "2.6.0",
  "uk.gov.hmrc" %% "agent-mtd-identifiers" % "0.10.0",
  "uk.gov.hmrc" %% "domain" % "5.1.0",
  "uk.gov.hmrc" %% "agent-kenshoo-monitoring" % "3.0.1",
  "uk.gov.hmrc" %% "play-reactivemongo" % "6.2.0",
  "uk.gov.hmrc" %% "mongo-lock" % "5.1.0",
  ws
)

def testDeps(scope: String) = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
  "org.scalatest" %% "scalatest" % "3.0.5" % scope,
  "org.mockito" % "mockito-core" % "2.18.0" % scope,
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
  "com.github.tomakehurst" % "wiremock" % "2.17.0" % scope,
  "org.pegdown" % "pegdown" % "1.6.0" % scope,
  "uk.gov.hmrc" %% "reactivemongo-test" % "2.0.0" % scope
)

lazy val root = (project in file("."))
  .settings(
    name := "agent-mapping",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.11.11",
    PlayKeys.playDefaultPort := 9439,
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.bintrayRepo("hmrc", "release-candidates"),
      Resolver.typesafeRepo("releases"),
      Resolver.jcenterRepo
    ),
    libraryDependencies ++= compileDeps ++ testDeps("test") ++ testDeps("it"),
    publishingSettings,
    scoverageSettings,
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    routesImport ++= Seq("uk.gov.hmrc.agentmapping.controller.UrlBinders._")
  )
  .configs(IntegrationTest)
  .settings(
    Keys.fork in IntegrationTest := false,
    Defaults.itSettings,
    unmanagedSourceDirectories in IntegrationTest += baseDirectory(_ / "it").value,
    parallelExecution in IntegrationTest := false,
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value)
  )
  .settings(scalariformItSettings)
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)

def oneForkedJvmPerTest(tests: Seq[TestDefinition]) = {
  tests.map { test =>
    new Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq(s"-Dtest.name=${test.name}"))))
  }
}