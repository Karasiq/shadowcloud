package com.karasiq.shadowcloud.utils

import scala.language.postfixOps
import scala.util.control.NonFatal

import com.typesafe.config.Config

private[shadowcloud] final class AppPasswordProvider(config: Config) {
  lazy val masterPassword: String = {
    try {
      config.getString("password")
    } catch { case NonFatal(_) ⇒
      "123456"
      //String.valueOf(System.console().readPassword("Enter master password: "))
    }
  }
}
