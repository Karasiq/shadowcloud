package com.karasiq.shadowcloud.config

import scala.concurrent.duration._
import scala.language.postfixOps

case class IndexConfig(syncInterval: FiniteDuration)

object IndexConfig extends ConfigImplicits {
  def apply(config: Config): IndexConfig = {
    IndexConfig(config.getFiniteDuration("sync-interval"))
  }
}