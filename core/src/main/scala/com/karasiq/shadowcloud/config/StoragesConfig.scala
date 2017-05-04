package com.karasiq.shadowcloud.config


import scala.language.postfixOps

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.providers.StorageProvider

private[shadowcloud] case class StoragesConfig(providers: ProvidersConfig[StorageProvider])

private[shadowcloud] object StoragesConfig extends ConfigImplicits {
  def apply(config: Config): StoragesConfig = {
    StoragesConfig(
      ProvidersConfig[StorageProvider](config.getConfig("providers"))
    )
  }
}