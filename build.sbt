import com.typesafe.sbt.packager.docker.Cmd
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

val commonSettings = Seq(
  organization := "com.github.karasiq",
  version := "1.2.7",
  scalaVersion := "2.12.4",
  // crossScalaVersions := Seq("2.11.11", "2.12.4"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0")),
  coverageExcludedPackages := "com.karasiq.shadowcloud.javafx.*;com.karasiq.shadowcloud.desktop.*;com.karasiq.shadowcloud.webapp.*;com.karasiq.shadowcloud.storage.*;com.karasiq.shadowcloud.persistence.*",
  //parallelExecution in test := false,
  //fork in test := false,
  scalacOptions ++= (if (sys.props.get("disable-assertions").contains("1"))
                       Seq("-Xelide-below", "OFF", "-Xdisable-assertions")
                     else
                       Nil),
  updateOptions := updateOptions.value.withLatestSnapshots(false),
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-unchecked",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-Ywarn-unused:-implicits",
    "-Xlint",
    "-Ypartial-unification",
    "-opt:l:inline",
    "-opt-inline-from:com.karasiq.**"
  )
)

val packageSettings = Seq(
  // javaOptions in Universal += "-Xmx2G",
  name in Universal := "shadowcloud",
  packageName := (name in Universal).value,
  version in Universal := version.value.replace("-SNAPSHOT", ""),
  maintainer := "Karasiq",
  packageSummary := "shadowcloud application",
  packageDescription := "shadowcloud - alternative cloud storage client.",
  jdkAppIcon := {
    lazy val iconExt = sys.props("os.name").toLowerCase match {
      case os if os.contains("mac") ⇒ "icns"
      case os if os.contains("win") ⇒ "ico"
      case _                        ⇒ "png"
    }
    Some(file(s"setup/icon.$iconExt"))
  },
  jdkPackagerType := "installer",
  jdkPackagerJVMArgs := Seq("-Xmx1G", "-XX:+UseG1GC", "-XX:+UseStringDeduplication"),
  jdkPackagerProperties := Map(
    "app.name"                 → "shadowcloud",
    "app.version"              → version.value.replace("-SNAPSHOT", ""),
    "file.encoding"            → "UTF-8",
    "java.net.preferIPv4Stack" → "true"
    // "jnrfuse.winfsp.path" → "C:\\Program Files (x86)\\WinFsp\\bin\\winfsp-x64.dll"
  ),
  // antPackagerTasks in JDKPackager := Some(file("/usr/lib/jvm/java-8-oracle/lib/ant-javafx.jar")),
  mappings in Universal += file("setup/shadowcloud_example.conf") → "shadowcloud_example.conf",
  javaOptions in Universal ++= Seq(
    "-Dfile.encoding=UTF-8",
    "-Djava.net.preferIPv4Stack=true"
  )
)

lazy val dockerSettings = Seq(
  dockerBaseImage := "openjdk:8-jre-slim",
  dockerExposedPorts := Seq(1911),
  dockerExposedVolumes := Seq("/opt/docker/sc"),
  dockerUsername := Some("karasiq"),
  dockerUpdateLatest := true,
  packageName in Docker := "shadowcloud",
  dockerEntrypoint ++= Seq(
    "-Dshadowcloud.external-config=/opt/docker/sc/shadowcloud.conf",
    "-Dshadowcloud.persistence.h2.path=/opt/docker/sc/shadowcloud",
    "-Dshadowcloud.drive.fuse.mount-path=/opt/docker/sc-drive",
    "-Dshadowcloud.drive.fuse.auto-mount=true",
    "-Dshadowcloud.http-server.host=0.0.0.0"
  ),
  dockerCommands := {
    val cmds = dockerCommands.value
    val injected = Seq(
      Cmd("RUN", "apt update && apt install -y fuse libfuse2 libfuse-dev python3-pip && rm -rf /var/lib/apt/lists/*"), // TODO https://github.com/docker/for-mac/issues/3431
      Cmd("RUN", "echo 'user_allow_other' >> /etc/fuse.conf"),
      Cmd(
        "RUN",
        "python3 -m pip install \"Telethon==1.14.0\" \"cryptg==0.2.post1\" \"Quart==0.12.0\" \"Hypercorn==0.9.5\" \"lz4==3.1.0\" \"pytz>=2020.1\""
      )
    )
    cmds.takeWhile(!_.makeContent.startsWith("USER 1001:0")) ++ injected ++ cmds.dropWhile(!_.makeContent.startsWith("USER 1001:0"))
  },
  libraryDependencies in Docker ++= (if (ProjectDeps.javacv.isEnabled) ProjectDeps.javacv.dockerPlatforms else Nil)
)

// -----------------------------------------------------------------------
// Shared
// -----------------------------------------------------------------------
lazy val model = crossProject(JSPlatform, JVMPlatform)
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
  .jvmSettings(libraryDependencies ++= ProjectDeps.akka.actors ++ ProjectDeps.protobuf ++ ProjectDeps.commonsConfigs)
  .jsSettings(ScalaJSDeps.akka.actors, ScalaJSDeps.protobuf, ScalaJSDeps.commonsConfigs)

lazy val modelJVM = model.jvm

lazy val modelJS = model.js

lazy val utils = crossProject(JSPlatform, JVMPlatform)
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

lazy val testUtils = (crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure) in file("utils") / "test")
  .settings(commonSettings, name := "shadowcloud-test-utils")
  .jvmSettings(
    libraryDependencies ++=
      ProjectDeps.scalaTest ++
        ProjectDeps.akka.testKit
  )
  .dependsOn(utils)

lazy val testUtilsJVM = testUtils.jvm

lazy val testUtilsJS = testUtils.js

lazy val serialization = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings, name := "shadowcloud-serialization")
  .jvmSettings(libraryDependencies ++= ProjectDeps.playJson ++ ProjectDeps.boopickle ++ ProjectDeps.kryo)
  .jsSettings(ScalaJSDeps.playJson, ScalaJSDeps.boopickle)
  .dependsOn(model, utils)

lazy val serializationJVM = serialization.jvm

lazy val serializationJS = serialization.js

// -----------------------------------------------------------------------
// Core
// -----------------------------------------------------------------------
lazy val core = project
  .settings(commonSettings)
  .settings(
    name := "shadowcloud-core",
    libraryDependencies ++= ProjectDeps.akka.all
  )
  .dependsOn(modelJVM, utilsJVM, serializationJVM, storageParent, cryptoParent, metadataParent, `cache-core`, testUtilsJVM % "test")

lazy val persistence = project
  .settings(commonSettings)
  .settings(
    name := "shadowcloud-persistence",
    libraryDependencies ++= ProjectDeps.akka.persistence ++ ProjectDeps.h2
  )
  .dependsOn(core)

lazy val coreAssembly = (project in file("core/assembly"))
  .settings(commonSettings, name := "shadowcloud-core-assembly")
  .dependsOn(
    core % "compile->compile;test->test",
    persistence,
    `cache-larray`,
    bouncyCastleCrypto,
    libsodiumCrypto,
    imageioMetadata,
    markdownMetadata,
    googleDriveStorage,
    mailruCloudStorage,
    dropboxStorage,
    webdavStorage,
    telegramStorage,
    yandexStorage
  )
  .dependsOn(
    Seq[ClasspathDep[ProjectReference]](javacvMetadata).filter(_ ⇒ ProjectDeps.javacv.isEnabled) ++
      Seq[ClasspathDep[ProjectReference]](tikaMetadata).filter(_ ⇒ sys.props.getOrElse("enable-tika", "1") == "1"): _*
  )
  .aggregate(
    core,
    persistence,
    bouncyCastleCrypto,
    libsodiumCrypto,
    tikaMetadata,
    imageioMetadata,
    markdownMetadata,
    javacvMetadata,
    googleDriveStorage,
    mailruCloudStorage,
    dropboxStorage,
    webdavStorage,
    telegramStorage,
    yandexStorage
  )

// -----------------------------------------------------------------------
// Cache
// -----------------------------------------------------------------------
lazy val `cache-core` = (project in file("cache") / "core")
  .settings(
    commonSettings,
    name := "shadowcloud-cache-core"
  )
  .dependsOn(modelJVM)

lazy val `cache-larray` = (project in file("cache") / "larray")
  .settings(
    commonSettings,
    name := "shadowcloud-cache-larray",
    libraryDependencies ++= ProjectDeps.larrayCache
  )
  .dependsOn(`cache-core`, utilsJVM)

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
  .settings(commonSettings, name := "shadowcloud-crypto-parent")
  .dependsOn(modelJVM)

lazy val bouncyCastleCrypto = cryptoPlugin("bouncycastle")
  .settings(libraryDependencies ++= ProjectDeps.bouncyCastle)
  .dependsOn(utilsJVM, testUtilsJVM % "test")

lazy val libsodiumCrypto = cryptoPlugin("libsodium")
  .settings(libraryDependencies ++= ProjectDeps.libSodiumJni)
  .dependsOn(utilsJVM)

// Storage plugins
def storagePlugin(id: String): Project = {
  val prefixedId = s"storage-$id"
  Project(prefixedId, file("storage") / id)
    .settings(
      commonSettings,
      name := s"shadowcloud-$prefixedId"
    )
    .dependsOn(core % "provided", storageParent % "provided", testUtilsJVM % "test")
}

lazy val storageParent = Project("storage-parent", file("storage") / "parent")
  .settings(commonSettings, name := "shadowcloud-storage-parent", libraryDependencies ++= ProjectDeps.akka.streams)
  .dependsOn(modelJVM, utilsJVM, testUtilsJVM % "test")

lazy val googleDriveStorage = storagePlugin("gdrive")
  .settings(libraryDependencies ++= ProjectDeps.gdrive)

lazy val dropboxStorage = storagePlugin("dropbox")
  .settings(libraryDependencies ++= ProjectDeps.dropbox)

lazy val mailruCloudStorage = storagePlugin("mailrucloud")
  .settings(libraryDependencies ++= ProjectDeps.mailrucloud)

lazy val webdavStorage = storagePlugin("webdav")
  .settings(libraryDependencies ++= ProjectDeps.sardine)

lazy val telegramStorage = storagePlugin("telegram")
  .settings(libraryDependencies ++= ProjectDeps.guava)

lazy val yandexStorage = storagePlugin("yandex")

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
  .settings(commonSettings, name := "shadowcloud-metadata-parent", libraryDependencies ++= ProjectDeps.akka.streams)
  .dependsOn(modelJVM, utilsJVM)

lazy val tikaMetadata = metadataPlugin("tika")
  .settings(libraryDependencies ++= ProjectDeps.tika)

lazy val imageioMetadata = metadataPlugin("imageio")
  .dependsOn(utilsJVM)

lazy val javacvMetadata = metadataPlugin("javacv")
  .settings(libraryDependencies ++= ProjectDeps.javacv.main)
  .dependsOn(utilsJVM)

lazy val markdownMetadata = metadataPlugin("markdown")
  .settings(libraryDependencies ++= ProjectDeps.flexmark)

// -----------------------------------------------------------------------
// HTTP
// -----------------------------------------------------------------------
lazy val autowireApi = (crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure) in (file("server") / "autowire-api"))
  .settings(commonSettings, name := "shadowcloud-autowire-api")
  .jvmSettings(libraryDependencies ++= ProjectDeps.autowire ++ ProjectDeps.scalaTest.map(_ % "test"))
  .jsSettings(ScalaJSDeps.autowire, ScalaJSDeps.browserDom, ScalaJSDeps.scalaTest)
  .dependsOn(model, serialization)

lazy val autowireApiJVM = autowireApi.jvm

lazy val autowireApiJS = autowireApi.js

lazy val server = project
  .settings(commonSettings, name := "shadowcloud-server")
  .dependsOn(`server-api-routes` % "compile->compile;test->test", `server-static-routes`, `server-webzinc-routes`)
  .aggregate(`server-api-routes`, `server-static-routes`, `server-webzinc-routes`)

lazy val `server-api-routes` = (project in file("server") / "api-routes")
  .settings(
    commonSettings,
    name := "shadowcloud-server-api",
    libraryDependencies ++=
      ProjectDeps.akka.streams ++
        ProjectDeps.akka.http ++
        ProjectDeps.akka.testKit.map(_ % "test")
  )
  .dependsOn(core % "compile->compile;test->test", autowireApiJVM, coreAssembly % "test")

lazy val `server-static-routes` = (project in file("server") / "static-routes")
  .settings(
    commonSettings,
    name := "shadowcloud-server-static",
    scalaJsBundlerAssets in Compile += {
      import com.karasiq.scalajsbundler.dsl._
      Bundle(
        "index",
        WebDeps.indexHtml,
        WebDeps.bootstrap,
        WebDeps.videoJS,
        WebDeps.markedJS,
        WebDeps.dropzoneJS,
        WebDeps.toastrJS,
        WebDeps.pellJS,
        WebDeps.multiSelectJS,
        scalaJsApplication(webapp, fastOpt = false, launcher = false).value
      )
    },
    scalaJsBundlerCompile in Compile := (scalaJsBundlerCompile in Compile)
      .dependsOn(fullOptJS in Compile in webapp)
      .value
  )
  .dependsOn(`server-api-routes`)
  .enablePlugins(SJSAssetBundlerPlugin)

lazy val `server-webzinc-routes` = (project in file("server") / "webzinc-routes")
  .settings(commonSettings, name := "shadowcloud-server-webzinc", libraryDependencies ++= ProjectDeps.webzinc)
  .dependsOn(`server-api-routes`)

lazy val webapp = (project in file("server") / "webapp")
  .settings(
    commonSettings,
    name := "shadowcloud-webapp",
    scalaJSUseMainModuleInitializer := true,
    ScalaJSDeps.bootstrap,
    ScalaJSDeps.videoJS,
    ScalaJSDeps.markedJS,
    ScalaJSDeps.java8Time,
    ScalaJSDeps.scalaCss
  )
  .dependsOn(autowireApiJS)
  .enablePlugins(ScalaJSPlugin)

// -----------------------------------------------------------------------
// shadowcloud-drive
// -----------------------------------------------------------------------
lazy val `drive-core` = (project in file("drive") / "core")
  .settings(
    commonSettings,
    name := "shadowcloud-drive-core",
    libraryDependencies ++= ProjectDeps.scalaTest.map(_ % "test")
  )
  .dependsOn(core % "compile->compile;test->test", coreAssembly % "test->test", utilsJVM)

lazy val `drive-fuse` = (project in file("drive") / "fuse")
  .settings(
    commonSettings,
    name := "shadowcloud-drive-fuse",
    resolvers += "jcenter" at "https://jcenter.bintray.com",
    libraryDependencies ++= ProjectDeps.`jnr-fuse`
  )
  .dependsOn(`drive-core`)

// -----------------------------------------------------------------------
// Desktop app
// -----------------------------------------------------------------------
lazy val javafx = (project in file("javafx"))
  .settings(commonSettings)
  .settings(
    name := "shadowcloud-javafx-gui",
    libraryDependencies ++= ProjectDeps.scalafx
  )
  .dependsOn(core)

lazy val desktopApp = (project in file("desktop-app"))
  .dependsOn(`larray-bytestring`, coreAssembly, server, javafx, `drive-fuse`)
  .settings(
    commonSettings,
    packageSettings,
    name := "shadowcloud-desktop",
    mainClass in Compile := Some("com.karasiq.shadowcloud.desktop.SCDesktopMain"),
    libraryDependencies ++= ProjectDeps.akka.slf4j ++ ProjectDeps.logback ++
      (if (ProjectDeps.javacv.isFullEnabled) ProjectDeps.javacv.mainPlatforms
       else if (ProjectDeps.javacv.isEnabled) ProjectDeps.javacv.currentPlatform
       else Nil),
    fork in run := true
  )
  .enablePlugins(JavaAppPackaging, ClasspathJarPlugin, JDKPackagerPlugin)

// -----------------------------------------------------------------------
// Console app
// -----------------------------------------------------------------------
lazy val consoleApp = (project in file("console-app"))
  .settings(
    commonSettings,
    packageSettings,
    dockerSettings,
    name := "shadowcloud-console",
    mainClass in Compile := Some("com.karasiq.shadowcloud.console.SCConsoleMain"),
    libraryDependencies ++= ProjectDeps.akka.slf4j ++ ProjectDeps.logback ++
      (if (ProjectDeps.javacv.isFullEnabled) ProjectDeps.javacv.mainPlatforms
       else if (ProjectDeps.javacv.isEnabled) ProjectDeps.javacv.currentPlatform
       else Nil),
    fork in run := true
  )
  .dependsOn(coreAssembly, server, `drive-fuse`)
  .enablePlugins(JavaAppPackaging, ClasspathJarPlugin, JDKPackagerPlugin, DockerPlugin)

// -----------------------------------------------------------------------
// Misc
// -----------------------------------------------------------------------
lazy val shell = project
  .settings(
    commonSettings,
    name := "shadowcloud-shell",
    mainClass in Compile := Some("com.karasiq.shadowcloud.test.Benchmark"),
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-simple" % "1.7.25"
    ),
    initialCommands in console :=
      """import com.karasiq.shadowcloud.shell.Shell._
        |init()
        |//test()
        |""".stripMargin
  )
  .dependsOn(coreAssembly, javafx)

lazy val shadowcloud = (project in file("."))
  .settings(
    commonSettings,
    name := "shadowcloud-root" /*,
    liquibaseUsername := "sa",
    liquibasePassword := s"${sys.props("shadowcloud.persistence.h2.password").ensuring(_.ne(null), "No password").replace(' ', '_')} sa",
    liquibaseDriver := "org.h2.Driver",
    liquibaseUrl := {
      val path = sys.props.getOrElse("shadowcloud.persistence.h2.path", s"${sys.props("user.home")}/.shadowcloud/shadowcloud")
      val cipher = sys.props.getOrElse("shadowcloud.persistence.h2.cipher", "AES")
      val compress = sys.props.getOrElse("shadowcloud.persistence.h2.compress", true)
      s"jdbc:h2:file:$path;CIPHER=$cipher;COMPRESS=$compress"
    },
    liquibaseChangelog := sourceDirectory.value / "migrations" / "changelog.sql" */
  )
  //.enablePlugins(com.github.sbtliquibase.SbtLiquibase)
  .aggregate(coreAssembly, `server-api-routes`)

lazy val `larray-bytestring` = project
  .settings(
    commonSettings,
    name := "larray-bytestring",
    libraryDependencies ++= ProjectDeps.larray
  )
