import sbt._

object ProjectDeps {
  type Deps = Seq[ModuleID]

  object Akka {
    private val akkaV = "2.4.17"
    private val akkaHttpV = "10.0.1"

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
  }

  def akka: Deps = Akka.all

  def kryo: Deps = Seq(
    // "com.esotericsoftware" % "kryo" % "4.0.0",
    "com.twitter" %% "chill" % "0.9.1"
  )

  def bouncyCastle: Deps = Seq(
    "org.bouncycastle" % "bcprov-jdk15on" % "1.56"
  )

  def scalaTest: Deps = Seq(
    "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  )
}