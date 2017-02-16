package com.karasiq.shadowcloud.config

import akka.actor.ActorContext

case class AppConfig(index: IndexConfig, hashing: HashingConfig)

object AppConfig extends ConfigImplicits {
  def apply(config: Config): AppConfig = {
    AppConfig(IndexConfig(config.getConfig("index")), HashingConfig(config.getConfig("hashing")))
  }

  def apply()(implicit context: ActorContext): AppConfig = {
    apply(context.system.settings.config.getConfig("shadowcloud"))
  }
}