package com.karasiq.shadowcloud.javafx

import akka.actor.ActorSystem
import com.karasiq.shadowcloud.ui.passwords.PasswordProvider
import javafx.stage.Stage

/** Shows JavaFX password dialog */
private[javafx] final class JavaFXPasswordProvider(actorSystem: ActorSystem) extends PasswordProvider {
  private[this] lazy val context = JavaFXContext(actorSystem)

  private[this] def showPasswordDialog(passwordId: String): Option[String] = {
    val dialog = new PasswordDialog(passwordId) {
      initOwner(context.app.stage)
    }
    dialog.showAndWait().filter(_.nonEmpty)
  }

  def askPassword(id: String): String = {
    context.assertInitialized()
    JFXUtils.runNow(showPasswordDialog(id).getOrElse(throw new IllegalArgumentException("No password provided")))
  }
}
