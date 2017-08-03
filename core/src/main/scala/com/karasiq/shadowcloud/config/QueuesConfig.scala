package com.karasiq.shadowcloud.config

import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits

case class QueuesConfig(rootConfig: Config,
                        storageWrite: Int,
                        storageRead: Int,
                        regionRepair: Int,
                        regionDiffs: Int,
                        regionDiffsTime: FiniteDuration,
                        chunksIndex: Int,
                        chunksIndexTime: FiniteDuration) extends WrappedConfig

object QueuesConfig extends ConfigImplicits with WrappedConfigFactory[QueuesConfig] {
  def apply(config: Config): QueuesConfig = {
    QueuesConfig(
      config,
      config.getInt("storage-write"),
      config.getInt("storage-read"),
      config.getInt("region-repair"),
      config.getInt("region-diffs"),
      config.getFiniteDuration("region-diffs-time"),
      config.getInt("chunks-index"),
      config.getFiniteDuration("chunks-index-time")
    )
  }
}
