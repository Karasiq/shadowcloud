package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.cache.CacheProvider

final case class CacheConfig(rootConfig: Config, provider: Class[CacheProvider], size: Int, sizeBytes: Long) extends WrappedConfig

object CacheConfig extends WrappedConfigFactory[CacheConfig] {
  def apply(config: Config): CacheConfig = {
    new CacheConfig(
      config,
      config.getClass("provider"),
      config.getInt("size"),
      config.getBytes("size-bytes")
    )
  }
}
