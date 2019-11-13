package com.karasiq.shadowcloud.server.http.config

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.config.{WrappedConfig, WrappedConfigFactory}
import com.typesafe.config.Config

final case class SCHttpServerConfig(rootConfig: Config, host: String, port: Int, multipartByteRanges: Boolean, passwordHash: Option[String])
    extends WrappedConfig

object SCHttpServerConfig extends WrappedConfigFactory[SCHttpServerConfig] {
  def apply(config: Config): SCHttpServerConfig = {
    SCHttpServerConfig(
      config,
      config.withDefault("127.0.0.1", _.getString("host")),
      config.withDefault(1911, _.getInt("port")),
      config.withDefault(true, _.getBoolean("multipart-byte-ranges")),
      config.optional(_.getString("password-hash"))
    )
  }
}
