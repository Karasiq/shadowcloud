package com.karasiq.shadowcloud.javafx

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.Duration
import scalafx.application.Platform
import scalafx.scene.control.{ButtonType, Dialog, PasswordField}
import scalafx.scene.control.ButtonBar.ButtonData
import scalafx.scene.layout.{GridPane, Priority}
import scalafx.Includes._
import scalafx.geometry.Insets
import scalafx.scene.image.ImageView

import akka.actor.ActorSystem

import com.karasiq.shadowcloud.config.passwords.PasswordProvider

/** Shows JavaFX password dialog */
private[javafx] final class JavaFXPasswordProvider(actorSystem: ActorSystem) extends PasswordProvider {
  private[this] val context = JavaFXContext(actorSystem)

  private[this] def showPasswordDialog(passwordId: String): Option[String] = {
    val dialog = new PasswordDialog(passwordId)
    dialog.initOwner(context.app.stage)
    dialog.showAndWait().filter(_.nonEmpty)
  }

  def askPassword(id: String): String = {
    val promise = Promise[String]
    Platform.runLater {
      showPasswordDialog(id) match {
        case Some(password) ⇒
          promise.success(password)

        case None ⇒
          promise.failure(new IllegalArgumentException("No password provided"))
      }
    }
    Await.result(promise.future, Duration.Inf)
  }

  private[this] class PasswordDialog(passwordId: String) extends Dialog[String] {
    def showAndWait(): Option[String] = {
      showAndWait((s: String) ⇒ s).asInstanceOf[Option[String]]
    }

    private[this] def getResourcePath(fileName: String) = {
      getClass.getClassLoader.getResource(fileName).toString
    }

    private[this] def initDialog(): Unit = {
      val passwordField = new PasswordField {
        hgrow = Priority.Always
        promptText = "Password"
      }

      val fieldGrid = new GridPane {
        hgap = 10
        vgap = 10
        padding = Insets(20, 10, 10, 10)
        add(passwordField, 0, 0)
      }

      title = "Password dialog"
      headerText = s"Enter $passwordId password"
      graphic = new ImageView(getResourcePath("key-icon_32.png"))

      val okButtonType = new ButtonType("OK", ButtonData.OKDone)
      dialogPane().buttonTypes += okButtonType

      val okButton = dialogPane().lookupButton(okButtonType)
      okButton.disable = true
      okButton.disable <== passwordField.text.isEmpty

      resultConverter = {
        case `okButtonType` ⇒
          passwordField.text()

        case _ ⇒
          ""
      }

      dialogPane().content = fieldGrid
      Platform.runLater(passwordField.requestFocus())
    }

    initDialog()
  }
}
