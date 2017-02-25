name := "shadowcloud-core"

libraryDependencies ++=
  ProjectDeps.akka.all ++
  ProjectDeps.kryo ++
  ProjectDeps.bouncyCastle ++
  ProjectDeps.scalaTest

libraryDependencies ++= Seq(
  "commons-codec" % "commons-codec" % "1.9"
)