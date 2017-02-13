name := "shadowcloud"

organization := "com.github.karasiq"

version := "1.0.0-SNAPSHOT"

isSnapshot := version.value.endsWith("SNAPSHOT")

scalaVersion := "2.12.1"

crossScalaVersions := Seq("2.11.8", "2.12.1")

resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= {
  val akkaV = "2.4.17"
  val akkaHttpV = "10.0.1"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % "test",
    "com.typesafe.akka" %% "akka-persistence" % akkaV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.lihaoyi" %% "upickle" % "0.4.4",
    "commons-codec" % "commons-codec" % "1.9",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    "org.bouncycastle" % "bcprov-jdk15on" % "1.56",
    "com.esotericsoftware" % "kryo" % "4.0.0",
    "com.twitter" %% "chill" % "0.9.1"
  )                       
}

mainClass in Compile := Some("com.karasiq.shadowcloud.test.Main")

licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0"))

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)