package com.karasiq.shadowcloud.config

import scala.concurrent.duration.FiniteDuration

import com.karasiq.shadowcloud.config.QueuesConfig.Config
import com.karasiq.shadowcloud.config.utils.ConfigImplicits

case class TimeoutsConfig(rootConfig: Config, chunkWrite: FiniteDuration, chunkRead: FiniteDuration) extends WrappedConfig

object TimeoutsConfig extends ConfigImplicits with WrappedConfigFactory[TimeoutsConfig] {
  def apply(config: Config): TimeoutsConfig = {
    TimeoutsConfig(
      config,
      config.getFiniteDuration("chunk-write"),
      config.getFiniteDuration("chunk-read")
    )
  }
}
