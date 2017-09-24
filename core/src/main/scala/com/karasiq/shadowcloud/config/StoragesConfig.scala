package com.karasiq.shadowcloud.config


import scala.language.postfixOps

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.providers.StorageProvider

private[shadowcloud] case class StoragesConfig(rootConfig: Config,
                                               providers: ProvidersConfig[StorageProvider]) extends WrappedConfig

private[shadowcloud] object StoragesConfig extends WrappedConfigFactory[StoragesConfig] with ConfigImplicits {
  def apply(config: Config): StoragesConfig = {
    StoragesConfig(
      config,
      ProvidersConfig.withType[StorageProvider](config.getConfig("providers"))
    )
  }
}