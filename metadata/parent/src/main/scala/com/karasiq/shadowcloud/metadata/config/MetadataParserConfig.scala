package com.karasiq.shadowcloud.metadata.config

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.config.{WrappedConfig, WrappedConfigFactory}
import com.karasiq.shadowcloud.utils.Utils

final case class MetadataParserConfig(rootConfig: Config, enabled: Boolean, extensions: Set[String], mimes: Set[String]) extends WrappedConfig {
  def canParse(name: String, mime: String): Boolean = {
    enabled && (extensions.contains(Utils.getFileExtensionLowerCase(name)) || mimes.contains(mime))
  }
}

object MetadataParserConfig extends WrappedConfigFactory[MetadataParserConfig] {
  def apply(config: Config): MetadataParserConfig = {
    MetadataParserConfig(
      config,
      config.withDefault(true, _.getBoolean("enabled")),
      config.withDefault(Set.empty, _.getStringSet("extensions")),
      config.withDefault(Set.empty, _.getStringSet("mimes"))
    )
  }
}