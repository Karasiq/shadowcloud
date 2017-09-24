package com.karasiq.shadowcloud.server.http

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.ShadowCloudExtension

trait SCHttpServerSettings {
  protected val sc: ShadowCloudExtension

  // -----------------------------------------------------------------------
  // Config
  // -----------------------------------------------------------------------
  protected object SCHttpSettings extends ConfigImplicits {
    val config = sc.config.rootConfig.getConfig("http-server")
    val useMultipartByteRanges = config.getBoolean("use-multipart-byte-ranges")
  }
}
