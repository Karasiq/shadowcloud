package com.karasiq.shadowcloud.ui.passwords.console

import com.karasiq.shadowcloud.ui.passwords.PasswordProvider

/** Uses [[java.io.Console#readPassword(java.lang.String, java.lang.Object...) readPassword]] function */
class ConsolePasswordProvider extends PasswordProvider {
  def askPassword(id: String): String = {
    new String(System.console().readPassword(s"Enter password ($id): "))
  }
}
