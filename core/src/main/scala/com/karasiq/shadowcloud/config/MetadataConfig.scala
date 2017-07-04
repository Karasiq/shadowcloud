package com.karasiq.shadowcloud.config

import com.typesafe.config.{Config, ConfigMemorySize}

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.metadata.MetadataProvider

case class MetadataConfig(rootConfig: Config, sizeLimit: Int, mimeProbeSize: Int,
                          providers: ProvidersConfig[MetadataProvider]) extends WrappedConfig

object MetadataConfig extends WrappedConfigFactory[MetadataConfig] with ConfigImplicits {
  def apply(config: Config): MetadataConfig = {
    def toBytesInt(value: ConfigMemorySize): Int = {
      math.min(value.toBytes, Int.MaxValue).toInt
    }

    MetadataConfig(
      config,
      toBytesInt(config.getMemorySize("size-limit")),
      toBytesInt(config.getMemorySize("mime-probe-size")),
      ProvidersConfig.withType[MetadataProvider](config.getConfig("providers"))
    )
  }
}