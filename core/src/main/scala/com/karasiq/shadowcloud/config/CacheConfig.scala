package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.cache.CacheProvider

case class CacheConfig(provider: Class[CacheProvider])

object CacheConfig {
  def apply(config: Config): CacheConfig = {
    new CacheConfig(config.getClass("provider"))
  }
}
