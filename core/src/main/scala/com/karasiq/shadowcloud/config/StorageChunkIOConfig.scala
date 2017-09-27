package com.karasiq.shadowcloud.config

import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits

@SerialVersionUID(0L)
final case class StorageChunkIOConfig(rootConfig: Config,
                                      readParallelism: Int, writeParallelism: Int,
                                      readQueueSize: Int, writeQueueSize: Int,
                                      readTimeout: FiniteDuration, writeTimeout: FiniteDuration) extends WrappedConfig

object StorageChunkIOConfig extends WrappedConfigFactory[StorageChunkIOConfig] with ConfigImplicits {
  def apply(config: Config): StorageChunkIOConfig = {
    StorageChunkIOConfig(
      config,
      config.getInt("read-parallelism"),
      config.getInt("write-parallelism"),
      config.getInt("read-queue-size"),
      config.getInt("write-queue-size"),
      config.getFiniteDuration("read-timeout"),
      config.getFiniteDuration("write-timeout")
    )
  }
}