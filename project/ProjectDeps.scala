import sbt._
import sbt.Keys.libraryDependencies

object ProjectDeps {
  type Deps = Seq[ModuleID]

  object akka {
    private val akkaV = "2.5.2"
    private val akkaHttpV = "10.0.7"

    def actors: Deps = Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-testkit" % akkaV % "test"
    )

    def streams: Deps = Seq(
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % "test"
    )

    def http: Deps = Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpV
    )

    def persistence: Deps = Seq(
      "com.typesafe.akka" %% "akka-persistence" % akkaV
    )

    def all: Deps = {
      actors ++ streams ++ http ++ persistence
    }

    def provided: Def.Setting[Seq[ModuleID]] = {
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % akkaV % "provided",
        "com.typesafe.akka" %% "akka-stream" % akkaV % "provided"
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

  def bouncyCastle: Deps = Seq(
    "org.bouncycastle" % "bcprov-jdk15on" % "1.57"
  )

  def libSodiumJni: Deps = Seq(
    "com.github.karasiq" % "kalium-fork" % "0.6.2" % "compile"
  )

  def h2: Deps = Seq(
    "com.h2database" % "h2" % "1.4.192",
    "io.getquill" %% "quill-jdbc" % "1.2.1"
  )
}