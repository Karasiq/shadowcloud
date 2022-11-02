package com.karasiq.shadowcloud.storage.gdrive

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.providers.StorageProvider

class GDriveStorageProvider(sc: ShadowCloudExtension) extends StorageProvider {
  override val storageTypes = Set("gdrive")

  override def storages: StoragePF = {
    case props if props.storageType == "gdrive" ⇒
      GDriveStoragePlugin(sc)
  }

  override def storageConfigs: StorageConfigPF = { case "gdrive" ⇒
    ConfigProps("type" → "gdrive", "credentials.login" → "example@gmail.com", "team-drive" → "")
  }
}
