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
    val host = config.withDefault("127.0.0.1", _.getString("host"))
    val port = config.withDefault(1911, _.getInt("port"))
    val useMultipartByteRanges = config.getBoolean("use-multipart-byte-ranges")
  }
}
