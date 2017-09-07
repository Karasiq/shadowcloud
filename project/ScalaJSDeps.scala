import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._
import sbt.Keys.libraryDependencies

object ScalaJSDeps {
  type Deps = Def.Setting[Seq[ModuleID]]
  object akka {
    val version = s"1.${ProjectDeps.akka.version}"

    def actors: Deps = {
      libraryDependencies ++= Seq(
        "org.akka-js" %%% "akkajsactor" % version,
        "org.akka-js" %%% "akkajstestkit" % version % "test"
      )
    }

    def streams: Deps = {
      libraryDependencies ++= Seq(
        "org.akka-js" %%% "akkajsactorstream" % version,
        "org.akka-js" %%% "akkajsstreamtestkit" % version % "test"
      )
    }
  }

  def browserDom: Deps = {
    libraryDependencies ++= Seq("org.scala-js" %%% "scalajs-dom" % "0.9.3")
  }

  def bootstrap: Deps = {
    libraryDependencies ++= Seq("com.github.karasiq" %%% "scalajs-bootstrap" % "2.1.4")
  }

  def scalaCss: Deps = {
    libraryDependencies ++= Seq("com.github.japgolly.scalacss" %%% "core" % "0.5.3")
  }

  def autowire: Deps = {
    libraryDependencies ++= Seq("com.lihaoyi" %%% "autowire" % "0.2.6")
  }

  def playJson: Deps = {
    libraryDependencies ++= Seq("com.typesafe.play" %%% "play-json" % "2.6.0" )
  }

  def protobuf: Deps = {
    libraryDependencies ++= Seq(
      "com.trueaccord.scalapb" %%% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion,
      "com.trueaccord.scalapb" %%% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion % "protobuf"
    )
  }

  def java8Time: Deps = {
    libraryDependencies ++= Seq("io.github.cquiroz" %%% "scala-java-time" % "2.0.0-M12")
  }
}