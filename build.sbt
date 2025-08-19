import uk.gov.hmrc.DefaultBuildSettings
import CodeCoverageSettings.scoverageSettings

val appName = "agent-mapping"

ThisBuild / majorVersion := 1
ThisBuild / scalaVersion := "2.13.16"
ThisBuild / scalafmtOnCompile := true

val scalaCOptions = Seq(
  "-Xfatal-warnings",
  "-Xlint:-missing-interpolator,_",
  "-Ywarn-value-discard",
  "-Ywarn-dead-code",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wconf:src=target/.*:s", // silence warnings from compiled files
  "-Wconf:src=routes/.*:s", // silence warnings from routes files
  "-Wconf:src=*html:w", // silence html warnings as they are wrong
  "-language:implicitConversions"
)

lazy val root = (project in file("."))
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    name := appName,
    organization := "uk.gov.hmrc",
    PlayKeys.playDefaultPort := 9439,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    resolvers ++= Seq(Resolver.typesafeRepo("releases")),
    routesImport ++= Seq("uk.gov.hmrc.agentmapping.controller.UrlBinders._"),
    scalacOptions ++= scalaCOptions,
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Test / parallelExecution := false,
    scoverageSettings
  )
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)


lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(root % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.test)
  .settings(
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true
  )

