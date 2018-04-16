package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.storage.replication.StorageSelector
import com.karasiq.shadowcloud.storage.replication.selectors.SimpleStorageSelector
import com.karasiq.shadowcloud.storage.utils.ChunkKeyMapper
import com.karasiq.shadowcloud.utils.Utils

@SerialVersionUID(0L)
final case class RegionConfig(rootConfig: Config,
                              chunkKey: Option[ChunkKeyMapper],
                              storageSelector: Class[StorageSelector],
                              dataReplicationFactor: Int,
                              indexReplicationFactor: Int,
                              garbageCollector: GCConfig) extends WrappedConfig

object RegionConfig extends WrappedConfigFactory[RegionConfig] with ConfigImplicits {
  val empty = apply(Utils.emptyConfig)

  def forId(regionId: RegionId, rootConfig: Config): RegionConfig = {
    val cfgObject = rootConfig.getConfigOrRef(s"regions.$regionId")
      .withFallback(rootConfig.getConfig("default-region"))
    apply(cfgObject)
  }

  def apply(config: Config): RegionConfig = {
    RegionConfig(
      config,
      config.optional(config ⇒ ChunkKeyMapper.forName(config.getString("chunk-key"), config)),
      config.withDefault(classOf[SimpleStorageSelector].asInstanceOf[Class[StorageSelector]], _.getClass("storage-selector")),
      config.withDefault(1, _.getInt("data-replication-factor")),
      config.withDefault(0, _.getInt("index-replication-factor")),
      GCConfig(config.getConfigIfExists("garbage-collector"))
    )
  }
}