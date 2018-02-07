package com.karasiq.shadowcloud.server.http.config

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.config.{WrappedConfig, WrappedConfigFactory}

final case class SCHttpServerConfig(rootConfig: Config, host: String, port: Int, multipartByteRanges: Boolean) extends WrappedConfig

object SCHttpServerConfig extends WrappedConfigFactory[SCHttpServerConfig] {
  def apply(config: Config): SCHttpServerConfig = {
    SCHttpServerConfig(
      config,
      config.withDefault("127.0.0.1", _.getString("host")),
      config.withDefault(1911, _.getInt("port")),
      config.withDefault(true, _.getBoolean("multipart-byte-ranges"))
    )
  }
}
