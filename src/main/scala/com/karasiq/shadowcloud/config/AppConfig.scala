package com.karasiq.shadowcloud.config

import akka.actor.ActorContext
import com.karasiq.shadowcloud.config.utils.ConfigImplicits

case class AppConfig(index: IndexConfig, hashing: HashingConfig, storage: StorageConfig)

object AppConfig extends ConfigImplicits {
  def apply(config: Config): AppConfig = {
    AppConfig(
      IndexConfig(config.getConfig("index")),
      HashingConfig(config.getConfig("hashing")),
      StorageConfig(config.getConfig("storage"))
    )
  }

  def apply()(implicit context: ActorContext): AppConfig = {
    apply(context.system.settings.config.getConfig("shadowcloud"))
  }
}