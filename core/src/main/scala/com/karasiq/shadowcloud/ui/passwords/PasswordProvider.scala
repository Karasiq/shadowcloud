package com.karasiq.shadowcloud.ui.passwords

trait PasswordProvider {
  def askPassword(id: String): String
}
