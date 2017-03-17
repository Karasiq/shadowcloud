import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys.libraryDependencies
import sbt._

object ScalaJSDeps {
  type Deps = Def.Setting[Seq[ModuleID]]
  object akka {
    private val akkaV = "0.2.4.16"
    
    def actors: Deps = {
      libraryDependencies ++= Seq(
        "org.akka-js" %%% "akkajsactor" % akkaV,
        "org.akka-js" %%% "akkajstestkit" % akkaV % "test"
      )
    }

    def streams: Deps = {
      libraryDependencies ++= Seq(
        "org.akka-js" %%% "akkajsactorstream" % akkaV,
        "org.akka-js" %%% "akkajsstreamtestkit" % akkaV % "test"
      )
    }
  }

  def bootstrap: Deps = {
    libraryDependencies ++= Seq("com.github.karasiq" %%% "scalajs-bootstrap" % "1.1.4")
  }
}