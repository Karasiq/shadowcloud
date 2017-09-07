import com.github.sbtliquibase.SbtLiquibase

val commonSettings = Seq(
  organization := "com.github.karasiq",
  version := "1.0.0-SNAPSHOT",
  isSnapshot := version.value.endsWith("SNAPSHOT"),
  scalaVersion := "2.12.3",
  // crossScalaVersions := Seq("2.11.8", "2.12.3"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0"))
)

// -----------------------------------------------------------------------
// Shared
// -----------------------------------------------------------------------
lazy val model = crossProject
  .crossType(CrossType.Pure)
  .settings(commonSettings, name := "shadowcloud-model")
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() → (sourceManaged in Compile).value
    ),
    PB.protoSources in Compile := Seq(
      (baseDirectory in Compile).value.getParentFile / "src" / "main" / "protobuf"
    )
  )
  .jvmSettings(libraryDependencies ++= ProjectDeps.akka.actors ++ ProjectDeps.protobuf)
  .jsSettings(ScalaJSDeps.akka.actors, ScalaJSDeps.protobuf)

lazy val modelJVM = model.jvm

lazy val modelJS = model.js

lazy val utils = crossProject
  .crossType(CrossType.Pure)
  .settings(commonSettings, name := "shadowcloud-utils")
  .jvmSettings(
    libraryDependencies ++=
      ProjectDeps.akka.streams ++
      ProjectDeps.lz4 ++
      Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
  )
  .dependsOn(model)

lazy val utilsJVM = utils.jvm

lazy val utilsJS = utils.js

lazy val testUtils = (crossProject.crossType(CrossType.Pure) in file("utils") / "test")
  .settings(commonSettings, name := "shadowcloud-test-utils")
  .jvmSettings(
    libraryDependencies ++=
      ProjectDeps.scalaTest ++
      ProjectDeps.akka.testKit
  )
  .dependsOn(utils)

lazy val testUtilsJVM = testUtils.jvm

lazy val testUtilsJS = testUtils.js 

// -----------------------------------------------------------------------
// Core
// -----------------------------------------------------------------------
lazy val core = project
  .settings(commonSettings)
  .settings(
    name := "shadowcloud-core",
    libraryDependencies ++= ProjectDeps.akka.all ++ ProjectDeps.kryo
  )
  .dependsOn(modelJVM, utilsJVM, storageParent, cryptoParent, metadataParent, testUtilsJVM % "test")

lazy val persistence = project
  .settings(commonSettings)
  .settings(
    name := "shadowcloud-persistence",
    libraryDependencies ++= ProjectDeps.akka.persistence ++ ProjectDeps.h2
  )
  .dependsOn(core)

lazy val coreAssembly = (project in file("target/core-assembly"))
  .settings(commonSettings, name := "shadowcloud-core-assembly")
  .dependsOn(
    core, persistence,
    bouncyCastleCrypto, libsodiumCrypto,
    tikaMetadata, imageioMetadata,
    testUtilsJVM % "test"
  )

// -----------------------------------------------------------------------
// Plugins
// -----------------------------------------------------------------------

// Crypto plugins
def cryptoPlugin(id: String): Project = {
  val prefixedId = s"crypto-$id"
  Project(prefixedId, file("crypto") / id)
    .settings(
      commonSettings,
      name := s"shadowcloud-$prefixedId"
    )
    .dependsOn(cryptoParent % "provided", testUtilsJVM % "test")
}

lazy val cryptoParent = Project("crypto-parent", file("crypto") / "parent")
  .settings(commonSettings)
  .dependsOn(modelJVM)

lazy val bouncyCastleCrypto = cryptoPlugin("bouncycastle")
  .settings(libraryDependencies ++= ProjectDeps.bouncyCastle)
  .dependsOn(testUtilsJVM % "test")

lazy val libsodiumCrypto = cryptoPlugin("libsodium")
  .settings(libraryDependencies ++= ProjectDeps.libSodiumJni)

// Storage plugins
lazy val storageParent = Project("storage-parent", file("storage") / "parent")
  .settings(commonSettings, libraryDependencies ++= ProjectDeps.akka.streams)
  .dependsOn(modelJVM, testUtilsJVM % "test")

// Metadata plugins
def metadataPlugin(id: String): Project = {
  val prefixedId = s"metadata-$id"
  Project(prefixedId, file("metadata") / id)
    .settings(
      commonSettings,
      name := s"shadowcloud-$prefixedId"
    )
    .dependsOn(metadataParent % "provided", testUtilsJVM % "test")
}

lazy val metadataParent = Project("metadata-parent", file("metadata") / "parent")
  .settings(commonSettings, libraryDependencies ++= ProjectDeps.akka.streams)
  .dependsOn(modelJVM)

lazy val tikaMetadata = metadataPlugin("tika")
  .settings(libraryDependencies ++= ProjectDeps.tika)

lazy val imageioMetadata = metadataPlugin("imageio")
  .dependsOn(utilsJVM)

// -----------------------------------------------------------------------
// HTTP
// -----------------------------------------------------------------------
lazy val autowireApi = (crossProject.crossType(CrossType.Pure) in (file("server") / "autowire-api"))
  .settings(commonSettings)
  .jvmSettings(libraryDependencies ++= ProjectDeps.autowire ++ ProjectDeps.playJson)
  .jsSettings(ScalaJSDeps.autowire, ScalaJSDeps.playJson, ScalaJSDeps.browserDom)
  .dependsOn(model)

lazy val autowireApiJVM = autowireApi.jvm

lazy val autowireApiJS = autowireApi.js

lazy val server = project
  .settings(commonSettings)
  .settings(
    name := "shadowcloud-server",
    libraryDependencies ++= ProjectDeps.akka.streams ++ ProjectDeps.akka.http,
    scalaJsBundlerAssets in Compile += {
      import com.karasiq.scalajsbundler.dsl._
      Bundle("index", WebDeps.bootstrap, WebDeps.indexHtml, scalaJsApplication(webapp, fastOpt = true, launcher = false).value)
    },
    scalaJsBundlerCompile in Compile <<= (scalaJsBundlerCompile in Compile)
      .dependsOn(fastOptJS in Compile in webapp)
  )
  .dependsOn(coreAssembly, javafx, autowireApiJVM)
  .enablePlugins(ScalaJSBundlerPlugin, JavaAppPackaging)

lazy val webapp = (project in file("server") / "webapp")
  .settings(commonSettings)
  .settings(
    name := "shadowcloud-webapp",
    scalaJSUseMainModuleInitializer := true,
    ScalaJSDeps.bootstrap,
    ScalaJSDeps.java8Time,
    ScalaJSDeps.scalaCss
  )
  .dependsOn(autowireApiJS)
  .enablePlugins(ScalaJSPlugin)

// -----------------------------------------------------------------------
// Misc
// -----------------------------------------------------------------------
lazy val javafx = (project in file("javafx"))
  .settings(commonSettings)
  .settings(
    name := "shadowcloud-javafx-gui",
    libraryDependencies ++= ProjectDeps.scalafx
  )
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
    liquibasePassword := s"${sys.props("shadowcloud.persistence.h2.password").ensuring(_.ne(null), "No password").replace(' ', '_')} sa",
    liquibaseDriver := "org.h2.Driver",
    liquibaseUrl := {
      val path = sys.props.getOrElse("shadowcloud.persistence.h2.path", s"${sys.props("user.home")}/.shadowcloud/shadowcloud")
      val cipher = sys.props.getOrElse("shadowcloud.persistence.h2.cipher", "AES")
      val compress = sys.props.getOrElse("shadowcloud.persistence.h2.compress", true)
      s"jdbc:h2:file:$path;CIPHER=$cipher;COMPRESS=$compress"
    },
    liquibaseChangelog := file("src/main/migrations/changelog.sql")
  )
  .dependsOn(coreAssembly, javafx)
  .enablePlugins(SbtLiquibase)