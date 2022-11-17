import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimumStmtTotal := 80.00,
    ScoverageKeys.coverageMinimumStmtPerFile := 80.00,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}

//lazy val wartRemoverSettings = {
//  val wartRemoverWarning = {
//    val warningWarts = Seq(
//      Wart.JavaSerializable,
//      Wart.StringPlusAny,
//      Wart.AsInstanceOf,
//      Wart.IsInstanceOf,
//      Wart.Any
//    )
//    Compile / compile / wartremoverWarnings ++= warningWarts
//  }

//  val wartRemoverError = {
//    // Error
//    val errorWarts = Seq(
//      Wart.ArrayEquals,
//      Wart.AnyVal,
//      Wart.EitherProjectionPartial,
//      Wart.Enumeration,
//      Wart.ExplicitImplicitTypes,
//      Wart.FinalVal,
//      Wart.JavaConversions,
//      Wart.JavaSerializable,
//      Wart.LeakingSealed,
//      Wart.MutableDataStructures,
//      Wart.Null,
//      Wart.OptionPartial,
//      Wart.Recursion,
//      Wart.Return,
//      Wart.TraversableOps,
//      Wart.TryPartial,
//      Wart.Var,
//      Wart.While)

    //Compile / compile / wartremoverErrors ++= errorWarts
  //}

//  Seq(
//    wartRemoverError,
//    wartRemoverWarning,
//    Test / compile / wartremoverErrors --= Seq(Wart.Any, Wart.Equals, Wart.Null, Wart.NonUnitStatements, Wart.PublicInference),
//    wartremoverExcluded ++=
//    routes.in(Compile).value ++
//    (baseDirectory.value / "it").get ++
//    (baseDirectory.value / "test").get ++
//    Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala")
//  )


val silencerVersion = "1.7.8"

lazy val compileDeps = Seq(
  "uk.gov.hmrc" %% "bootstrap-backend-play-28" % "7.11.0",
  "uk.gov.hmrc" %% "auth-client" % "5.14.0-play-28",
  "uk.gov.hmrc" %% "agent-mtd-identifiers" % "0.47.0-play-28",
  "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"      % "0.74.0",
  "com.typesafe.play" %% "play-json" % "2.9.2",
  "uk.gov.hmrc" %% "agent-kenshoo-monitoring" % "4.8.0-play-28",
  ws
)

def testDeps(scope: String) = Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % scope,
  "org.scalatestplus" %% "mockito-3-12" % "3.2.10.0" % scope,
  "com.github.tomakehurst" % "wiremock-jre8" % "2.26.1" % scope,
  "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % "0.74.0" % scope,
  "com.vladsch.flexmark" %  "flexmark-all" % "0.35.10" % scope
)

lazy val root = (project in file("."))
  .settings(
    name := "agent-mapping",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.12.15",
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-Xlint:-missing-interpolator,_",
      "-Yno-adapted-args",
      "-Ywarn-value-discard",
      "-Ywarn-dead-code",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
      "-P:silencer:pathFilters=views;routes"),
    PlayKeys.playDefaultPort := 9439,
    resolvers := Seq(
      Resolver.typesafeRepo("releases")
    ),
    resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2",
    resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns),
    resolvers += "HMRC-local-artefacts-maven" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases-local",
    libraryDependencies ++= compileDeps ++ testDeps("test") ++ testDeps("it"),
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    ),
    publishingSettings,
    scoverageSettings,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    routesImport ++= Seq("uk.gov.hmrc.agentmapping.controller.UrlBinders._"),
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true
  )
  .configs(IntegrationTest)
  .settings(
    majorVersion := 0,
    IntegrationTest / Keys.fork := true,
    Defaults.itSettings,
    IntegrationTest / unmanagedSourceDirectories += baseDirectory(_ / "it").value,
    IntegrationTest / parallelExecution := false,
    IntegrationTest / scalafmtOnCompile := true
  )
  //.settings(wartRemoverSettings: _*)
  .enablePlugins(PlayScala, SbtDistributablesPlugin)

inConfig(IntegrationTest)(scalafmtCoreSettings)
