package com.karasiq.shadowcloud.javafx

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.Duration
import scalafx.application.Platform
import scalafx.scene.control.{ButtonType, Dialog, PasswordField}
import scalafx.scene.control.ButtonBar.ButtonData
import scalafx.scene.layout.GridPane
import scalafx.Includes._

import akka.actor.ActorSystem

import com.karasiq.shadowcloud.config.passwords.PasswordProvider

/** Shows JavaFX password dialog */
private[javafx] final class JavaFXPasswordProvider(actorSystem: ActorSystem) extends PasswordProvider {
  private[this] val context = JavaFXContext(actorSystem)

  private[this] def showPasswordDialog(passwordId: String): Option[String] = {
    val dialog: Dialog[String] = new Dialog[String] {
      initOwner(context.app.stage)

      val passwordField = new PasswordField {
        promptText = "Password"
      }

      val grid = new GridPane {
        add(passwordField, 0, 0)
      }

      title = "Password dialog"
      headerText = s"Enter password ($passwordId): "

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

      dialogPane().content = grid
      Platform.runLater(passwordField.requestFocus())
    }
    dialog.showAndWait().asInstanceOf[Option[String]].filter(_.nonEmpty)
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
}
