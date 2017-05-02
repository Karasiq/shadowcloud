package com.karasiq.shadowcloud.config


import scala.concurrent.duration._
import scala.language.postfixOps

import com.karasiq.shadowcloud.config.utils.ConfigImplicits

private[shadowcloud] case class IndexConfig(syncInterval: FiniteDuration, replicationFactor: Int, compactThreshold: Int)

private[shadowcloud] object IndexConfig extends ConfigImplicits {
  def apply(config: Config): IndexConfig = {
    IndexConfig(config.getFiniteDuration("sync-interval"), config.getInt("replication-factor"), config.getInt("compact-threshold"))
  }
}