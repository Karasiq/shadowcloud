import com.github.sbtliquibase.SbtLiquibase

val commonSettings = Seq(
  organization := "com.github.karasiq",
  version := "1.0.0-SNAPSHOT",
  isSnapshot := version.value.endsWith("SNAPSHOT"),
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.11.8", "2.12.1"),
  licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0"))
)

// -----------------------------------------------------------------------
// Shared
// -----------------------------------------------------------------------
lazy val model = crossProject
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() → (sourceManaged in Compile).value
    ),
    PB.protoSources in Compile := Seq(
      (baseDirectory in Compile).value.getParentFile / "src" / "main" / "protobuf"
    ),
    libraryDependencies ++= ProjectDeps.protobuf
  )
  .jvmSettings(libraryDependencies ++= ProjectDeps.akka.actors)
  .jsSettings(ScalaJSDeps.akka.actors)

lazy val modelJVM = model.jvm

lazy val modelJS = model.js

// -----------------------------------------------------------------------
// Core
// -----------------------------------------------------------------------
lazy val core = project
  .settings(commonSettings)
  .dependsOn(modelJVM, storageParent, cryptoParent, metadataParent, bouncyCastleCrypto, libSodiumCrypto)

lazy val persistence = project
  .settings(commonSettings)
  .dependsOn(core)

// -----------------------------------------------------------------------
// Plugins
// -----------------------------------------------------------------------
def cryptoPlugin(id: String): Project = {
  val prefixedId = s"crypto-$id"
  Project(prefixedId, file("crypto") / id)
    .settings(
      commonSettings,
      name := s"shadowcloud-$prefixedId",
      libraryDependencies ++= ProjectDeps.scalaTest
    )
    .dependsOn(cryptoParent % "provided")
}

def metadataPlugin(id: String): Project = {
  val prefixedId = s"metadata-$id"
  Project(prefixedId, file("metadata") / id)
    .settings(
      commonSettings,
      name := s"shadowcloud-$prefixedId",
      libraryDependencies ++= ProjectDeps.scalaTest
    )
    .dependsOn(metadataParent % "provided")
}

lazy val cryptoParent = Project("crypto-parent", file("crypto") / "parent")
  .settings(commonSettings)
  .dependsOn(modelJVM)

lazy val bouncyCastleCrypto = cryptoPlugin("bouncycastle")

lazy val libSodiumCrypto = cryptoPlugin("libsodium")

lazy val storageParent = Project("storage-parent", file("storage") / "parent")
  .settings(commonSettings, libraryDependencies ++= ProjectDeps.akka.streams)
  .dependsOn(modelJVM)

lazy val metadataParent = Project("metadata-parent", file("metadata") / "parent")
  .settings(commonSettings)
  .dependsOn(modelJVM)

lazy val tikaMetadata = metadataPlugin("tika")

// -----------------------------------------------------------------------
// HTTP
// -----------------------------------------------------------------------
lazy val server = project
  .settings(commonSettings)
  .settings(
    scalaJsBundlerAssets in Compile += {
      import com.karasiq.scalajsbundler.dsl._
      Bundle("index", WebDeps.bootstrap, WebDeps.indexHtml, scalaJsApplication(webapp, fastOpt = true).value)
    },
    scalaJsBundlerCompile in Compile <<= (scalaJsBundlerCompile in Compile)
      .dependsOn(fastOptJS in Compile in webapp)
  )
  .dependsOn(core)
  .enablePlugins(ScalaJSBundlerPlugin, JavaAppPackaging)

lazy val webapp = (project in file("server") / "webapp")
  .settings(commonSettings)
  .enablePlugins(ScalaJSPlugin)

// -----------------------------------------------------------------------
// Misc
// -----------------------------------------------------------------------
lazy val javafx = (project in file("javafx"))
  .settings(commonSettings)
  .dependsOn(core)

lazy val shell = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "shadowcloud-shell",
    mainClass in Compile := Some("com.karasiq.shadowcloud.test.Benchmark"),
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.25"
    ),
    initialCommands in console :=
      """import com.karasiq.shadowcloud.shell.Shell._
        |init()
        |test()
        |""".stripMargin,
    liquibaseUsername := "sa",
    liquibasePassword := s"${sys.props("shadowcloud.master-password").ensuring(_.ne(null), "No password").replace(' ', '_')} sa",
    liquibaseDriver := "org.h2.Driver",
    liquibaseUrl := {
      val path = sys.props.getOrElse("shadowcloud.persistence.h2.path", s"${sys.props("user.home")}/.shadowcloud/shadowcloud")
      val cipher = sys.props.getOrElse("shadowcloud.persistence.h2.cipher", "AES")
      val compress = sys.props.getOrElse("shadowcloud.persistence.h2.compress", true)
      s"jdbc:h2:file:$path;CIPHER=$cipher;COMPRESS=$compress"
    },
    liquibaseChangelog := file("src/main/migrations/changelog.sql")
  )
  .dependsOn(core, persistence, javafx)
  .enablePlugins(SbtLiquibase)