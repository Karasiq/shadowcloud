package com.karasiq.shadowcloud.storage.telegram

import com.karasiq.shadowcloud.providers.StorageProvider

class TelegramStorageProvider extends StorageProvider {
  override val storageTypes = Set("telegram")

  override def storages: StoragePF = {
    case sp if sp.storageType == "telegram" ⇒
      new TelegramStoragePlugin()
  }
}
