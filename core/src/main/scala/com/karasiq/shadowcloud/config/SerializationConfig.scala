package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.compression.StreamCompression
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.config.SerializationConfig.Config

case class SerializationConfig(rootConfig: Config,
                               frameLimit: Int,
                               compression: StreamCompression.CompressionType.Value) extends WrappedConfig

object SerializationConfig extends WrappedConfigFactory[SerializationConfig] with ConfigImplicits {
  def apply(config: Config): SerializationConfig = {
    SerializationConfig(
      config,
      config.getBytesInt("frame-limit"),
      config.optional(_.getString("compression"))
        .map(StreamCompression.CompressionType.withName)
        .getOrElse(StreamCompression.CompressionType.none)
    )
  }
}
