package com.karasiq.shadowcloud.config.utils

import com.karasiq.shadowcloud.utils.Utils

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try
import scala.util.control.NonFatal

object ConfigImplicits extends ConfigImplicits

trait ConfigImplicits {
  protected type Config = com.typesafe.config.Config
  protected implicit class ConfigOps(private[this] val config: Config) {
    def getFiniteDuration(path: String): FiniteDuration = {
      Utils.toScalaDuration(config.getDuration(path))
    }

    def getConfigOrRef(path: String): Config = {
      val value = Try(config.getConfig(path))
      if (value.isSuccess) {
        value.get
      } else {
        val path1 = config.getString(path)
        // getConfigOrRefRec(path1)
        config.getConfig(path1)
      }
    }

    @inline 
    def withDefault[T](default: T, value: Config ⇒ T): T = {
      try {
        value(config)
      } catch {
        case NonFatal(_) ⇒ default
      }
    }
  }
}
