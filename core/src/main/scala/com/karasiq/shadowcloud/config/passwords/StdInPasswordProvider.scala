package com.karasiq.shadowcloud.config.passwords

import scala.io.StdIn

/** Uses [[scala.io.StdIn#readLine(java.lang.String, scala.collection.Seq) readLine]] function */
class StdInPasswordProvider extends PasswordProvider {
  def askPassword(id: String): String = {
    StdIn.readLine(s"Enter password ($id): ")
  }
}
