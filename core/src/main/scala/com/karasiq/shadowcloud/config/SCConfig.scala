package com.karasiq.shadowcloud.config

import com.karasiq.common.configs.ConfigImplicits
import com.typesafe.config.Config

private[shadowcloud] case class SCConfig(
    rootConfig: Config,
    chunks: ChunksConfig,
    crypto: CryptoConfig,
    storage: StoragesConfig,
    metadata: MetadataConfig,
    parallelism: ParallelismConfig,
    queues: QueuesConfig,
    timeouts: TimeoutsConfig,
    serialization: SerializationConfig,
    persistence: PersistenceConfig,
    cache: CacheConfig,
    ui: UIConfig,
    misc: MiscConfig
) extends WrappedConfig

private[shadowcloud] object SCConfig extends WrappedConfigFactory[SCConfig] with ConfigImplicits {
  def apply(config: Config): SCConfig = {
    SCConfig(
      config,
      ChunksConfig(config.getConfig("chunks")),
      CryptoConfig(config.getConfig("crypto")),
      StoragesConfig(config.getConfig("storage")),
      MetadataConfig(config.getConfig("metadata")),
      ParallelismConfig(config.getConfig("parallelism")),
      QueuesConfig(config.getConfig("queues")),
      TimeoutsConfig(config.getConfig("timeouts")),
      SerializationConfig(config.getConfig("serialization")),
      PersistenceConfig(config.getConfig("persistence")),
      CacheConfig(config.getConfig("cache")),
      UIConfig(config.getConfig("ui")),
      MiscConfig(config.getConfig("misc"))
    )
  }
}
