package com.karasiq.shadowcloud.config

import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits

@SerialVersionUID(0L)
final case class StorageIndexConfig(rootConfig: Config,
                                    syncInterval: FiniteDuration,
                                    snapshotThreshold: Int,
                                    compactThreshold: Int,
                                    compactDeleteOld: Boolean,
                                    keepDeleted: Boolean)

object StorageIndexConfig extends ConfigImplicits {
  def apply(config: Config): StorageIndexConfig = {
    StorageIndexConfig(
      config,
      config.getFiniteDuration("sync-interval"),
      config.getInt("snapshot-threshold"),
      config.getInt("compact-threshold"),
      config.getBoolean("compact-delete-old"),
      config.getBoolean("keep-deleted")
    )
  }
}
