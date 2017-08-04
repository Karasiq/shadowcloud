import sbt._
import sbt.Keys.libraryDependencies

object ProjectDeps {
  type Deps = Seq[ModuleID]

  object akka {
    val version = "2.5.2"
    val httpVersion = "10.0.9"

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
      "com.typesafe.akka" %% "akka-testkit" % version % "test",
      "com.typesafe.akka" %% "akka-stream-testkit" % version % "test"
    )

    def all: Deps = {
      actors ++ streams ++ http ++ persistence ++ testKit
    }

    def provided: Def.Setting[Seq[ModuleID]] = {
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % version % "provided",
        "com.typesafe.akka" %% "akka-stream" % version % "provided"
      )
    }
  }

  def scalaTest: Deps = Seq(
    "org.scalatest" %% "scalatest" % "3.0.3" % "test"
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

  def bouncyCastle: Deps = Seq(
    "org.bouncycastle" % "bcprov-jdk15on" % "1.57"
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
}