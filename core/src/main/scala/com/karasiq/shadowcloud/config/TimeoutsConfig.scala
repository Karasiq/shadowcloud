package com.karasiq.shadowcloud.config

import scala.concurrent.duration.FiniteDuration

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.config.QueuesConfig.Config

case class TimeoutsConfig(
    rootConfig: Config,
    query: FiniteDuration,
    chunkWrite: FiniteDuration,
    chunkRead: FiniteDuration,
    chunksList: FiniteDuration,
    chunksDelete: FiniteDuration,
    regionChunkWrite: FiniteDuration,
    regionChunkRead: FiniteDuration,
    indexWrite: FiniteDuration,
    indexRead: FiniteDuration,
    indexList: FiniteDuration,
    synchronize: FiniteDuration,
    collectGarbage: FiniteDuration
) extends WrappedConfig

object TimeoutsConfig extends ConfigImplicits with WrappedConfigFactory[TimeoutsConfig] {
  def apply(config: Config): TimeoutsConfig = {
    TimeoutsConfig(
      config,
      config.getFiniteDuration("query"),
      config.getFiniteDuration("chunk-write"),
      config.getFiniteDuration("chunk-read"),
      config.getFiniteDuration("chunks-list"),
      config.getFiniteDuration("chunks-delete"),
      config.getFiniteDuration("region-chunk-write"),
      config.getFiniteDuration("region-chunk-read"),
      config.getFiniteDuration("index-write"),
      config.getFiniteDuration("index-read"),
      config.getFiniteDuration("index-list"),
      config.getFiniteDuration("synchronize"),
      config.getFiniteDuration("collect-garbage")
    )
  }
}
