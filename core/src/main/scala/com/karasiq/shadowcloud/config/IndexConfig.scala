package com.karasiq.shadowcloud.config


import com.karasiq.shadowcloud.config.utils.ConfigImplicits

import scala.concurrent.duration._
import scala.language.postfixOps

case class IndexConfig(syncInterval: FiniteDuration, replicationFactor: Int)

object IndexConfig extends ConfigImplicits {
  def apply(config: Config): IndexConfig = {
    IndexConfig(config.getFiniteDuration("sync-interval"), config.getInt("replication-factor"))
  }
}