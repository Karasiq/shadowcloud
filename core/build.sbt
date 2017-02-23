name := "shadowcloud-core"

libraryDependencies ++= ProjectDeps.akka ++
  ProjectDeps.kryo ++
  ProjectDeps.bouncyCastle ++
  ProjectDeps.scalaTest

libraryDependencies ++= Seq(
  "commons-codec" % "commons-codec" % "1.9"
)