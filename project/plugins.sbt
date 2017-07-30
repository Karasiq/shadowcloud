logLevel := Level.Warn

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.8")

libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.0-pre4"

addSbtPlugin("com.github.sbtliquibase" % "sbt-liquibase" % "0.2.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.19")

addSbtPlugin("com.github.karasiq" % "sbt-scalajs-bundler" % "1.0.7")

libraryDependencies += "com.lihaoyi" %% "scalatags" % "0.5.4"