package com.karasiq.shadowcloud.javafx

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.NonFatal
import scalafx.application.{JFXApp, Platform}
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.stage.StageStyle

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}

object JavaFXContext extends ExtensionId[JavaFXContextExtension] with ExtensionIdProvider {
  def createExtension(system: ExtendedActorSystem): JavaFXContextExtension = {
    new JavaFXContextExtension(system)
  }

  def lookup(): ExtensionId[_ <: Extension] = {
    JavaFXContext
  }
}

final class JavaFXContextExtension(system: ExtendedActorSystem) extends Extension {
  private[this] val initPromise = Promise[Boolean]

  object app extends JFXApp {
    stage = new PrimaryStage {
      initStyle(StageStyle.Transparent)
      scene = new Scene {
        onShowing = { _ ⇒
          Platform.runLater(stage.hide())
          initPromise.success(true)
        }
      }
    }
  }

  //noinspection ConvertExpressionToSAM
  private[this] def startJavaFxApp(): Unit = {
    Platform.implicitExit = false
    val thread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          app.main(Array.empty)
        } catch {
          case NonFatal(ex) ⇒
            initPromise.failure(ex)
        }
      }
    })
    system.registerOnTermination(thread.interrupt())
    thread.start()
    Await.result(initPromise.future, 1 minute)
  }

  startJavaFxApp()
}