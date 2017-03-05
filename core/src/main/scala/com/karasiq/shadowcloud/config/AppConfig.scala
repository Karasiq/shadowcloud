package com.karasiq.shadowcloud.config

import akka.actor.{ActorContext, ActorSystem}
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.typesafe.config.ConfigFactory

case class AppConfig(index: IndexConfig, crypto: CryptoConfig, storage: StorageConfig, parallelism: ParallelismConfig)

object AppConfig extends ConfigImplicits {
  def apply(config: Config): AppConfig = {
    AppConfig(
      IndexConfig(config.getConfig("index")),
      CryptoConfig(config.getConfig("crypto")),
      StorageConfig(config.getConfig("storage")),
      ParallelismConfig(config.getConfig("parallelism"))
    )
  }

  def apply(actorSystem: ActorSystem): AppConfig = {
    apply(actorSystem.settings.config.getConfig("shadowcloud"))
  }

  def apply()(implicit context: ActorContext): AppConfig = {
    apply(context.system)
  }

  def load(): AppConfig = {
    apply(ConfigFactory.load().getConfig("shadowcloud"))
  }
}