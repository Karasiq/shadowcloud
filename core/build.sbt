name := "shadowcloud-core"

libraryDependencies ++=
  ProjectDeps.akka.all ++
  ProjectDeps.kryo ++
  ProjectDeps.scalaTest

libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value