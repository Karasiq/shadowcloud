package com.karasiq.shadowcloud.ui.console

import akka.actor.ActorSystem
import com.karasiq.shadowcloud.ui.UIProvider

// Uses Akka logging for output
class HeadlessUIProvider(as: ActorSystem) extends UIProvider {
  override def showErrorMessage(error: Throwable): Unit = {
    as.log.error(error, "Unhandled application error")
  }
}
