logLevel := Level.Warn

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.14")

addSbtPlugin("com.github.karasiq" % "sbt-scalajs-bundler" % "1.0.7")

libraryDependencies += "com.lihaoyi" %% "scalatags" % "0.5.4"