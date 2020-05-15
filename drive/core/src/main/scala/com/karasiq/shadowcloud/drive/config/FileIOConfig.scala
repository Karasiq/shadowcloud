package com.karasiq.shadowcloud.drive.config

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.config.{WrappedConfig, WrappedConfigFactory}
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

final case class FileIOConfig(
    rootConfig: Config,
    flushInterval: FiniteDuration,
    flushLimit: Long,
    readTimeout: FiniteDuration,
    writeTimeout: FiniteDuration,
    createMetadata: Boolean
) extends WrappedConfig

object FileIOConfig extends WrappedConfigFactory[FileIOConfig] {
  def apply(config: Config): FileIOConfig = {
    FileIOConfig(
      config,
      config.getFiniteDuration("flush-interval"),
      config.getBytes("flush-limit"),
      config.getFiniteDuration("read-timeout"),
      config.getFiniteDuration("write-timeout"),
      config.getBoolean("create-metadata")
    )
  }
}
