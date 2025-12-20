package com.karasiq.shadowcloud.storage.telegram

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.storage.telegram.TelegramStorageConfig.Secrets
import com.typesafe.config.Config

case class TelegramStorageConfig(pythonPath: Option[String], entity: String, port: Option[Int], tempDir: String)

object TelegramStorageConfig {

  def apply(config: Config): TelegramStorageConfig = {
    TelegramStorageConfig(
      config.optional(_.getString("python-path")),
      config.withDefault("tgcloud", _.getString("entity")),
      config.optional(_.getInt("port")),
      config.getString("temp-dir")
    )
  }
}
