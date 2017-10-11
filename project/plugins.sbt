logLevel := Level.Warn

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.8")

libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.0-pre4"

addSbtPlugin("com.github.sbtliquibase" % "sbt-liquibase" % "0.2.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.2")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.20")

addSbtPlugin("com.github.karasiq" % "sbt-scalajs-bundler" % "1.0.7")

addSbtPlugin("pl.project13.sbt" % "sbt-jol" % "0.1.2")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "scalatags" % "0.5.4",
  "org.webjars.bower" % "marked" % "0.3.5",
  "org.webjars" % "highlightjs" % "9.2.0"
)