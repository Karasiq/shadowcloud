val commonSettings = Seq(
  organization := "com.github.karasiq",
  version := "1.0.0-SNAPSHOT",
  isSnapshot := version.value.endsWith("SNAPSHOT"),
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.11.8", "2.12.1"),
  licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0"))
)

lazy val core = (project in file("core"))
  .settings(commonSettings)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "shadowcloud",
    mainClass in Compile := Some("com.karasiq.shadowcloud.test.Main")
  )
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)