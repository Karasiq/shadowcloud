package com.karasiq.shadowcloud.config.utils

import com.karasiq.shadowcloud.utils.Utils

import scala.concurrent.duration._
import scala.language.postfixOps

trait ConfigImplicits {
  protected type Config = com.typesafe.config.Config
  protected implicit class ConfigOps(private[this] val config: Config) {
    def getFiniteDuration(path: String): FiniteDuration = {
      Utils.toScalaDuration(config.getDuration(path))
    }
  }
}
