package com.karasiq.shadowcloud.config

import scala.concurrent.duration.FiniteDuration

import com.karasiq.shadowcloud.config.QueuesConfig.Config
import com.karasiq.shadowcloud.config.utils.ConfigImplicits

case class TimeoutsConfig(rootConfig: Config,
                          query: FiniteDuration,
                          chunkWrite: FiniteDuration,
                          chunkRead: FiniteDuration,
                          chunkDelete: FiniteDuration) extends WrappedConfig

object TimeoutsConfig extends ConfigImplicits with WrappedConfigFactory[TimeoutsConfig] {
  def apply(config: Config): TimeoutsConfig = {
    TimeoutsConfig(
      config,
      config.getFiniteDuration("query"),
      config.getFiniteDuration("chunk-write"),
      config.getFiniteDuration("chunk-read"),
      config.getFiniteDuration("chunk-delete")
    )
  }
}
