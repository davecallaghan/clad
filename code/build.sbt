val scala3Version = "3.3.7"

lazy val commonSettings = Seq(
  scalaVersion := scala3Version,
  organization := "com.twocdata",
  version      := "0.1.0-SNAPSHOT"
)

val http4sVersion = "0.23.30"
val catsEffectVersion = "3.5.7"

lazy val core = project
  .in(file("core"))
  .settings(
    commonSettings,
    name := "clad-core"
  )

lazy val `core-test` = project
  .in(file("core-test"))
  .dependsOn(core)
  .settings(
    commonSettings,
    name := "clad-core-test",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"       % "3.2.19",
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0",
      "org.scalacheck" %% "scalacheck"      % "1.18.1"
    )
  )

lazy val evaluation = project
  .in(file("evaluation"))
  .dependsOn(core)
  .settings(
    commonSettings,
    name := "clad-evaluation"
  )

lazy val `evaluation-test` = project
  .in(file("evaluation-test"))
  .dependsOn(evaluation, `core-test`)
  .settings(
    commonSettings,
    name := "clad-evaluation-test",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"       % "3.2.19",
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0",
      "org.scalacheck" %% "scalacheck"      % "1.18.1"
    )
  )

lazy val runtime = project
  .in(file("runtime"))
  .dependsOn(core, evaluation)
  .settings(
    commonSettings,
    name := "clad-runtime",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % "4.1.0"
    )
  )

lazy val `runtime-test` = project
  .in(file("runtime-test"))
  .dependsOn(runtime, `evaluation-test`)
  .settings(
    commonSettings,
    name := "clad-runtime-test",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"       % "3.2.19",
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0",
      "org.scalacheck" %% "scalacheck"      % "1.18.1"
    )
  )

lazy val audit = project
  .in(file("audit"))
  .dependsOn(runtime)
  .settings(
    commonSettings,
    name := "clad-audit"
  )

lazy val `audit-test` = project
  .in(file("audit-test"))
  .dependsOn(audit, `runtime-test`)
  .settings(
    commonSettings,
    name := "clad-audit-test",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"       % "3.2.19",
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0",
      "org.scalacheck" %% "scalacheck"      % "1.18.1"
    )
  )

lazy val output = project
  .in(file("output"))
  .dependsOn(core, evaluation, runtime)
  .settings(
    commonSettings,
    name := "clad-output"
  )

lazy val `output-test` = project
  .in(file("output-test"))
  .dependsOn(output, `runtime-test`)
  .settings(
    commonSettings,
    name := "clad-output-test",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"       % "3.2.19",
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0",
      "org.scalacheck" %% "scalacheck"      % "1.18.1"
    )
  )

lazy val integrity = project
  .in(file("integrity"))
  .dependsOn(audit, runtime, output)
  .settings(
    commonSettings,
    name := "clad-integrity"
  )

lazy val `integrity-test` = project
  .in(file("integrity-test"))
  .dependsOn(integrity, `runtime-test`, `audit-test`)
  .settings(
    commonSettings,
    name := "clad-integrity-test",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"       % "3.2.19",
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0",
      "org.scalacheck" %% "scalacheck"      % "1.18.1"
    )
  )

lazy val monitoring = project
  .in(file("monitoring"))
  .dependsOn(integrity, audit, runtime, output, core)
  .settings(
    commonSettings,
    name := "clad-monitoring"
  )

lazy val `monitoring-test` = project
  .in(file("monitoring-test"))
  .dependsOn(monitoring, `integrity-test`, `runtime-test`)
  .settings(
    commonSettings,
    name := "clad-monitoring-test",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"       % "3.2.19",
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0",
      "org.scalacheck" %% "scalacheck"      % "1.18.1"
    )
  )

lazy val config = project
  .in(file("config"))
  .dependsOn(core, evaluation, runtime, output, integrity)
  .settings(
    commonSettings,
    name := "clad-config"
  )

lazy val `config-test` = project
  .in(file("config-test"))
  .dependsOn(config, `runtime-test`)
  .settings(
    commonSettings,
    name := "clad-config-test",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"       % "3.2.19",
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0",
      "org.scalacheck" %% "scalacheck"      % "1.18.1"
    )
  )

lazy val api = project
  .in(file("api"))
  .dependsOn(config, runtime, output, monitoring, integrity)
  .settings(
    commonSettings,
    name := "clad-api",
    libraryDependencies ++= Seq(
      "org.http4s"    %% "http4s-ember-server" % http4sVersion,
      "org.http4s"    %% "http4s-dsl"          % http4sVersion,
      "org.typelevel" %% "cats-effect"         % catsEffectVersion
    )
  )

lazy val `api-test` = project
  .in(file("api-test"))
  .dependsOn(api, `config-test`)
  .settings(
    commonSettings,
    name := "clad-api-test",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"            % "3.2.19",
      "org.http4s"     %% "http4s-ember-client"  % http4sVersion  % Test,
      "org.typelevel"  %% "cats-effect-testing-scalatest" % "1.6.0" % Test
    )
  )

lazy val mcp = project
  .in(file("mcp"))
  .dependsOn(config, runtime, output, audit, integrity)
  .settings(
    commonSettings,
    name := "clad-mcp"
  )

lazy val `mcp-test` = project
  .in(file("mcp-test"))
  .dependsOn(mcp, `config-test`)
  .settings(
    commonSettings,
    name := "clad-mcp-test",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"       % "3.2.19",
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0",
      "org.scalacheck" %% "scalacheck"      % "1.18.1"
    )
  )

lazy val difftest = project
  .in(file("difftest"))
  .dependsOn(core, `core-test`)
  .settings(
    commonSettings,
    name := "clad-difftest",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % "4.1.0",
      "org.scalatest"  %% "scalatest"       % "3.2.19",
      "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0",
      "org.scalacheck" %% "scalacheck"      % "1.18.1"
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(core, `core-test`, evaluation, `evaluation-test`, runtime, `runtime-test`, audit, `audit-test`, output, `output-test`, integrity, `integrity-test`, monitoring, `monitoring-test`, config, `config-test`, api, `api-test`, mcp, `mcp-test`, difftest)
  .settings(
    commonSettings,
    name := "clad",
    publish / skip := true
  )
