package com.karasiq.shadowcloud.config

import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits

case class QueuesConfig(
    rootConfig: Config,
    regionRepair: Int,
    regionDiffs: Int,
    regionDiffsTime: FiniteDuration,
    chunksIndex: Int,
    chunksIndexTime: FiniteDuration
) extends WrappedConfig

object QueuesConfig extends ConfigImplicits with WrappedConfigFactory[QueuesConfig] {
  def apply(config: Config): QueuesConfig = {
    QueuesConfig(
      config,
      config.getInt("region-repair"),
      config.getInt("region-diffs"),
      config.getFiniteDuration("region-diffs-time"),
      config.getInt("chunks-index"),
      config.getFiniteDuration("chunks-index-time")
    )
  }
}
