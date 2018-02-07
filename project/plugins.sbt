logLevel := Level.Warn

addSbtPlugin("com.github.sbtliquibase" % "sbt-liquibase" % "0.2.0") // TODO: https://github.com/sbtliquibase/sbt-liquibase-plugin/issues/17

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.3")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.22")

addSbtPlugin("com.github.karasiq" % "sbt-scalajs-bundler" % "1.2.1")

addSbtPlugin("pl.project13.sbt" % "sbt-jol" % "0.1.2")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "scalatags" % "0.5.4",
  "org.webjars.bower" % "marked" % "0.3.5",
  "org.webjars" % "highlightjs" % "9.2.0"
)