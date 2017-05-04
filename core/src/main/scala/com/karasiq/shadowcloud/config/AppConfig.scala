package com.karasiq.shadowcloud.config

import akka.actor.{ActorContext, ActorSystem}
import com.typesafe.config.ConfigFactory

import com.karasiq.shadowcloud.config.utils.ConfigImplicits

private[shadowcloud] case class AppConfig(crypto: CryptoConfig, storage: StoragesConfig, parallelism: ParallelismConfig)

private[shadowcloud] object AppConfig extends ConfigImplicits {
  private[config] val ROOT_CFG_PATH = "shadowcloud"

  def apply(config: Config): AppConfig = {
    AppConfig(
      CryptoConfig(config.getConfig("crypto")),
      StoragesConfig(config.getConfig("storage")),
      ParallelismConfig(config.getConfig("parallelism"))
    )
  }

  def apply(actorSystem: ActorSystem): AppConfig = {
    apply(actorSystemConfig(ROOT_CFG_PATH)(actorSystem))
  }

  def apply()(implicit context: ActorContext): AppConfig = {
    apply(context.system)
  }

  def load(): AppConfig = {
    apply(ConfigFactory.load().getConfig(ROOT_CFG_PATH))
  }
}