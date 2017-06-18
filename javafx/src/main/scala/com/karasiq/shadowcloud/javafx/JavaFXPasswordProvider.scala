package com.karasiq.shadowcloud.javafx

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.Duration
import scalafx.application.Platform

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
}
