package com.karasiq.shadowcloud.drive.config

import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.config.{WrappedConfig, WrappedConfigFactory}


final case class FileIOConfig(rootConfig: Config, flushInterval: FiniteDuration, flushLimit: Long, timeout: FiniteDuration, createMetadata: Boolean) extends WrappedConfig

object FileIOConfig extends WrappedConfigFactory[FileIOConfig] {
  def apply(config: Config): FileIOConfig = {
    FileIOConfig(
      config,
      config.getFiniteDuration("flush-interval"),
      config.getBytes("flush-limit"),
      config.getFiniteDuration("timeout"),
      config.getBoolean("create-metadata")
    )
  }
}
