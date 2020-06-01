package com.karasiq.shadowcloud.storage.telegram

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.providers.StorageProvider

class TGCloudStorageProvider extends StorageProvider {
  override val storageTypes = Set("tgcloud")

  override def storages = {
    case sp if sp.storageType == "tgcloud" ⇒
      new TGCloudStoragePlugin()
  }

  override def storageConfigs = {
    case "tgcloud" ⇒
      ConfigProps("type" → "tgcloud")
  }
}
