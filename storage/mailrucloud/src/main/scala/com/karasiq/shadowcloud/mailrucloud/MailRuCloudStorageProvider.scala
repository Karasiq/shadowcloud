package com.karasiq.shadowcloud.mailrucloud

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.providers.StorageProvider

class MailRuCloudStorageProvider extends StorageProvider {
  override val storageTypes = Set("mailrucloud")

  override def storages = {
    case sp if sp.storageType == "mailrucloud" ⇒
      MailRuCloudStoragePlugin()
  }

  override def storageConfigs = { case "mailrucloud" ⇒
    ConfigProps("type" → "mailrucloud", "credentials.login" → "example@mail.ru", "credentials.password" → "123456")
  }
}
