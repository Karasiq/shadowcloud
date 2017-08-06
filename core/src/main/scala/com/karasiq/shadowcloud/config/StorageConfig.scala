package com.karasiq.shadowcloud.config

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.ChunkKeyMapper

case class StorageConfig(rootConfig: Config, syncInterval: FiniteDuration,
                         indexCompactThreshold: Int, indexSnapshotThreshold: Int,
                         chunkKey: ChunkKeyMapper) extends WrappedConfig

object StorageConfig extends WrappedConfigFactory[StorageConfig] with ConfigImplicits {
  private[this] def getConfigForId(storageId: String, rootConfig: Config): Config = {
    rootConfig.getConfigOrRef(s"storages.$storageId")
      .withFallback(rootConfig.getConfig("default-storage"))
  }

  def forId(storageId: String, rootConfig: Config): StorageConfig = {
    val config = getConfigForId(storageId, rootConfig)
    apply(config)
  }

  def forProps(storageId: String, props: StorageProps, rootConfig: Config): StorageConfig = {
    val config = props.rootConfig.getConfigOrRef("custom-config")
      .withFallback(getConfigForId(storageId, rootConfig))
    apply(config)
  }

  def apply(config: Config): StorageConfig = {
    StorageConfig(
      config,
      config.getFiniteDuration("sync-interval"),
      config.getInt("index-compact-threshold"),
      config.getInt("index-snapshot-threshold"),
      createChunkMapper(config, config.getString("chunk-key"))
    )
  }

  private[this] def createChunkMapper(config: Config, str: String): ChunkKeyMapper = str match {
    // Predefined
    case "hash" ⇒ ChunkKeyMapper.hash
    case "encrypted-hash" ⇒ ChunkKeyMapper.encryptedHash
    case "double-hash" ⇒ ChunkKeyMapper.doubleHash

    // Custom class
    case _ ⇒
      val mapperClass = Class.forName(str).asSubclass(classOf[ChunkKeyMapper])
      Try(mapperClass.getConstructor(classOf[Config]).newInstance(config))
        .orElse(Try(mapperClass.newInstance()))
        .getOrElse(throw new InstantiationException("No appropriate constructor found for " + mapperClass))
  }
}