package com.karasiq.shadowcloud.ui.passwords.console

import com.karasiq.shadowcloud.ui.passwords.PasswordProvider

import scala.io.StdIn

/** Uses [[scala.io.StdIn#readLine(java.lang.String, scala.collection.Seq) readLine]] function */
class StdInPasswordProvider extends PasswordProvider {
  def askPassword(id: String): String = {
    StdIn.readLine(s"Enter password ($id): ")
  }
}
