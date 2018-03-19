package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.providers.SessionProvider

final case class PersistenceConfig(rootConfig: Config,
                                   sessionProvider: Class[SessionProvider],
                                   journalPlugin: String,
                                   snapshotPlugin: String) extends WrappedConfig

object PersistenceConfig extends WrappedConfigFactory[PersistenceConfig] with ConfigImplicits {
  def apply(config: Config) = {
    PersistenceConfig(
      config,
      config.getClass("session-provider"),
      config.withDefault("", _.getString("journal-plugin")),
      config.withDefault("", _.getString("snapshot-plugin"))
    )
  }
}
