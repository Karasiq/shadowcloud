package com.karasiq.shadowcloud.server.http

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.server.http.config.SCHttpServerConfig

trait SCHttpServerSettings {
  protected val sc: ShadowCloudExtension

  // -----------------------------------------------------------------------
  // Config
  // -----------------------------------------------------------------------
  lazy val httpServerConfig = SCHttpServerConfig(sc.config.rootConfig.getConfigIfExists("http-server"))
}
