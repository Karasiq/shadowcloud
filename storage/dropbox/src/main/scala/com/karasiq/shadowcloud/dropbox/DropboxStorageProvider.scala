package com.karasiq.shadowcloud.dropbox

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.providers.StorageProvider

class DropboxStorageProvider extends StorageProvider {
  override val storageTypes = Set("dropbox")

  override val storages: StoragePF = {
    case s if s.storageType == "dropbox" ⇒
      DropboxStoragePlugin()
  }

  override val storageConfigs: StorageConfigPF = {
    case "dropbox" ⇒
      ConfigProps("type" → "dropbox", "credentials.login" → "example@mail.com")
  }
}