logLevel := Level.Warn

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.34")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.9.0" // Should go before Scala.js

// addSbtPlugin("com.github.sbtliquibase" % "sbt-liquibase" % "0.2.0") // TODO: https://github.com/sbtliquibase/sbt-liquibase-plugin/issues/17

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.32")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1")

addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "0.6.1")

addSbtPlugin("com.github.karasiq" % "sbt-scalajs-bundler" % "1.2.2")

// addSbtPlugin("pl.project13.sbt" % "sbt-jol" % "0.1.2")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.3.0"
