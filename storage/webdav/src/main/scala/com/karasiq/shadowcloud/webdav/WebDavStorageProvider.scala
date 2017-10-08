package com.karasiq.shadowcloud.webdav

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.providers.StorageProvider

class WebDavStorageProvider extends StorageProvider {
  override val storageTypes = Set("webdav")

  override def storages = {
    case sp if sp.storageType == "webdav" ⇒
      WebDavStoragePlugin()
  }

  override def storageConfigs = {
    case "webdav" ⇒
      ConfigProps("type" → "webdav", "address.uri" → "https://example.com/dir", "credentials.login" → "example", "credentials.password" → "123456")
  }
}
