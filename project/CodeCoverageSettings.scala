import sbt.Def
import scoverage.ScoverageKeys

object CodeCoverageSettings {

  lazy val scoverageSettings: Seq[Def.Setting[? >: String & Double & Boolean]] = {
    Seq(
      // Semicolon-separated list of regexs matching classes to exclude
      ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*;.*TestOnlyController""",
      ScoverageKeys.coverageMinimumStmtTotal := 80.00,
      ScoverageKeys.coverageFailOnMinimum := true,
      ScoverageKeys.coverageHighlighting := true,
    )
  }
  
}
