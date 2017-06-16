package com.karasiq.shadowcloud.config.passwords

trait PasswordProvider {
  def askPassword(id: String): String
}
