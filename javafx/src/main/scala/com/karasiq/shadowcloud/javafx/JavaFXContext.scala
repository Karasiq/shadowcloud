package com.karasiq.shadowcloud.javafx

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import javafx.stage.WindowEvent
import scalafx.application.JFXApp.PrimaryStage
import scalafx.application.{JFXApp, Platform}
import scalafx.scene.Scene
import scalafx.scene.image.Image
import scalafx.stage.{Modality, StageStyle}

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.control.NonFatal

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
  val initFuture                = initPromise.future

  object app extends JFXApp {
    stage = new PrimaryStage { stage ⇒
      initStyle(StageStyle.Transparent)
      icons.add(new Image(JFXUtils.getResourcePath("sc-javafx/sc-icon.png")))
      scene = new Scene {
        onShowing = { _ ⇒
          Platform.runLater {
            stage.hide()
            initPromise.success(true)
          }
        }
      }

      addEventHandler(
        WindowEvent.WINDOW_SHOWN,
        { _: WindowEvent ⇒
          import javafx.stage.Screen
          val screenBounds = Screen.getPrimary.getVisualBounds
          stage.x = (screenBounds.getWidth - stage.width()) / 2
          stage.y = (screenBounds.getHeight - stage.height()) / 2
        }
      )
    }
  }

  //noinspection ConvertExpressionToSAM
  private[this] def startJavaFxApp(): Unit = {
    val thread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          Platform.implicitExit = false
          app.main(Array.empty)
        } catch {
          case NonFatal(ex) ⇒
            initPromise.tryFailure(ex)
        }
      }
    })

    system.registerOnTermination {
      Platform.exit()
      thread.interrupt()
    }

    thread.start()
    assertInitialized()
  }

  def assertInitialized(): Unit = {
    Await.result(initFuture, 1 minute)
  }

  startJavaFxApp()
}
