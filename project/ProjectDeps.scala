import sbt._

object ProjectDeps {
  type Deps = Seq[ModuleID]

  object akka {
    val version = "2.5.6"
    val httpVersion = "10.0.11"

    def actors: Deps = Seq(
      "com.typesafe.akka" %% "akka-actor" % version
    )

    def streams: Deps = Seq(
      "com.typesafe.akka" %% "akka-stream" % version
    )

    def http: Deps = Seq(
      "com.typesafe.akka" %% "akka-http" % httpVersion
    )

    def persistence: Deps = Seq(
      "com.typesafe.akka" %% "akka-persistence" % version
    )

    def testKit: Deps = Seq(
      "com.typesafe.akka" %% "akka-testkit" % version,
      "com.typesafe.akka" %% "akka-stream-testkit" % version,
      "com.typesafe.akka" %% "akka-http-testkit" % httpVersion
    )

    def slf4j: Deps = Seq(
      "com.typesafe.akka" %% "akka-slf4j" % version
    )

    def all: Deps = {
      actors ++ streams ++ http ++ persistence // ++ testKit.map(_ % "test")
    }
  }

  def scalaTest: Deps = Seq(
    "org.scalatest" %% "scalatest" % "3.0.3"
  )

  def kryo: Deps = Seq(
    // "com.esotericsoftware" % "kryo" % "4.0.0",
    "com.twitter" %% "chill" % "0.9.2",
    "com.twitter" %% "chill-akka" % "0.9.2"
  )

  def protobuf: Deps = Seq(
    "com.trueaccord.scalapb" %% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion,
    "com.trueaccord.scalapb" %% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion % "protobuf"
  )

  def autowire: Deps = Seq(
    "com.lihaoyi" %% "autowire" % "0.2.6"
  )

  def playJson: Deps = Seq(
    "com.typesafe.play" %% "play-json" % "2.6.0"
  )

  def boopickle: Deps = Seq(
    "io.suzaku" %% "boopickle" % "1.2.6"
  )

  def bouncyCastle: Deps = Seq(
    "org.bouncycastle" % "bcprov-jdk15on" % "1.58"
  )

  def libSodiumJni: Deps = Seq(
    "com.github.karasiq" % "kalium-fork" % "0.6.2" % "compile"
  )

  // http://h2database.com/html/main.html
  def h2: Deps = Seq(
    "com.h2database" % "h2" % "1.4.192",
    "io.getquill" %% "quill-jdbc" % "1.2.1"
  )

  // https://tika.apache.org/
  def tika: Deps = Seq(
    "org.apache.tika" % "tika-parsers" % "1.16",
    "org.apache.tika" % "tika-core" % "1.16"
  )

  // https://github.com/lz4/lz4-java
  def lz4: Deps = Seq(
    "org.lz4" % "lz4-java" % "1.4.0"
  )

  def scalafx: Deps = Seq(
    "org.scalafx" %% "scalafx" % "8.0.102-R11"
  )

  def commonsConfigs: Deps = Seq(
    "com.github.karasiq" %% "commons-configs" % "1.0.8"
  )

  def gdrive: Deps = Seq(
    "com.github.karasiq" %% "gdrive-api" % "1.0.12"
  )

  def mailrucloud: Deps = Seq(
    "com.github.karasiq" %% "mailrucloud-api" % "1.1.0"
  )

  def dropbox: Deps = Seq(
    "com.github.karasiq" %% "dropbox-api" % "1.0.1"
  )

  // https://github.com/lookfirst/sardine/
  def sardine: Deps = Seq(
    "com.github.lookfirst" % "sardine" % "5.7"
  )

  def logback: Deps = Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3"
  )

  def javacv: Deps = Seq(
    "org.bytedeco" % "javacv-platform" % "1.3.3"
  )

  def apacheCommonsIO: Deps = Seq(
    "commons-io" % "commons-io" % "2.6"
  )

  // https://github.com/vsch/flexmark-java
  def flexmark: Deps = Seq(
    "com.vladsch.flexmark" % "flexmark-all" % "0.28.4"
  )

  def webzinc: Deps = Seq(
    "com.github.karasiq" %% "webzinc" % "1.0.3"
  )
}