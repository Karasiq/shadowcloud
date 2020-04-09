package com.karasiq.shadowcloud.drive.config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.config.WrappedConfig
import com.typesafe.config.Config

final case class SCDriveConfig(rootConfig: Config, blockSize: Int, fileIO: FileIOConfig) extends WrappedConfig

object SCDriveConfig extends ConfigImplicits {
  def apply(config: Config): SCDriveConfig = {
    SCDriveConfig(
      config,
      config.getMemorySize("block-size").toBytes.toInt,
      FileIOConfig(config.getConfig("file-io"))
    )
  }
}
