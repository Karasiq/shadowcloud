package com.karasiq.shadowcloud.drive.config

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.config.WrappedConfig

final case class SCDriveConfig(rootConfig: Config, fileIO: FileIOConfig) extends WrappedConfig

object SCDriveConfig extends ConfigImplicits {
  def apply(config: Config): SCDriveConfig = {
    SCDriveConfig(
      config,
      FileIOConfig(config.getConfig("file-io"))
    )
  }
}
