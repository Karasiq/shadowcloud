package com.karasiq.shadowcloud.ui.passwords.console

import com.karasiq.shadowcloud.ui.passwords.PasswordProvider

/** Uses [[java.io.Console#readPassword(java.lang.String, java.lang.Object...) readPassword]] function */
class ConsolePasswordProvider extends PasswordProvider {
  private[this] lazy val default = new StdInPasswordProvider()

  def askPassword(id: String): String = System.console() match {
    case null => default.askPassword(id)
    case console => new String(console.readPassword(s"Enter password ($id): "))
  }
}
