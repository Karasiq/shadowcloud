package com.karasiq.shadowcloud.storage.yandex

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.providers.StorageProvider

class YandexStorageProvider extends StorageProvider {
  override def storageTypes: Set[String] = Set("yandex")

  override def storages: StoragePF = {
    case props if props.storageType == "yandex" =>
      new YandexStoragePlugin
  }

  override def storageConfigs: StorageConfigPF = {
    case "yandex" => ConfigProps(
      "type" -> "yandex",
      "credentials.login" -> "johndoe123",
      "credentials.password" -> "passw0rd"
    )
  }
}
