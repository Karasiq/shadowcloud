package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.providers.SessionProvider

final case class PersistenceConfig(rootConfig: Config, sessionProvider: Class[SessionProvider]) extends WrappedConfig

object PersistenceConfig extends WrappedConfigFactory[PersistenceConfig] with ConfigImplicits {
  def apply(config: Config) = {
    PersistenceConfig(
      config,
      config.getClass("session-provider")
    )
  }
}
