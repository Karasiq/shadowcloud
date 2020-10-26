package com.karasiq.shadowcloud.storage.telegram

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.storage.telegram.TelegramStorageConfig.Secrets
import com.typesafe.config.Config

case class TelegramStorageConfig(pythonPath: Option[String], entity: String, port: Option[Int], secrets: Secrets, tempDir: String)

object TelegramStorageConfig {
  case class Secrets(apiId: Int, apiHash: String, entity: String)
  object Secrets {
    def apply(config: Config): Secrets = {
      Secrets(
        config.getInt("api-id"),
        config.getString("api-hash"),
        config.getString("entity")
      )
    }
  }

  def apply(config: Config): TelegramStorageConfig = {
    TelegramStorageConfig(
      config.optional(_.getString("python-path")),
      config.withDefault("tgcloud", _.getString("entity")),
      config.optional(_.getInt("port")),
      Secrets(config.getConfig("secrets")),
      config.getString("temp-dir")
    )
  }
}
