package com.karasiq.shadowcloud.javafx

import java.io.{ByteArrayOutputStream, PrintStream}

import akka.actor.ActorSystem
import com.karasiq.shadowcloud.ui.UIProvider

private[javafx] final class JavaFXUIProvider(actorSystem: ActorSystem) extends UIProvider {
  private[this] lazy val context = JavaFXContext(actorSystem)

  override def showErrorMessage(error: Throwable): Unit = synchronized {
    context.assertInitialized()
    val message = {
      val stream = new ByteArrayOutputStream()
      val pw     = new PrintStream(stream)
      error.printStackTrace(pw)
      new String(stream.toByteArray)
    }
    ErrorAlert.show(context.app.stage, message)
  }

  override def showNotification(text: String): Unit = synchronized {
    context.assertInitialized()
    NotifyAlert.show(context.app.stage, text)
  }
}
