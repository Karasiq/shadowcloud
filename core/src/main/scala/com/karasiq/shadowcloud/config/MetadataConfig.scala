package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.metadata.MetadataProvider

case class MetadataConfig(rootConfig: Config, sizeLimit: Long, mimeProbeSize: Int,
                          providers: ProvidersConfig[MetadataProvider]) extends WrappedConfig

object MetadataConfig extends WrappedConfigFactory[MetadataConfig] with ConfigImplicits {
  def apply(config: Config): MetadataConfig = {
    MetadataConfig(
      config,
      config.getBytes("size-limit"),
      config.getBytesInt("mime-probe-size"),
      ProvidersConfig.withType[MetadataProvider](config.getConfig("providers"))
    )
  }
}