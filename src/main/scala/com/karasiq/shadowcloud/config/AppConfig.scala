package com.karasiq.shadowcloud.config

import akka.actor.ActorContext

case class AppConfig(index: IndexConfig)

object AppConfig extends ConfigImplicits {
  def apply(config: Config): AppConfig = {
    AppConfig(IndexConfig(config.getConfig("index")))
  }

  def apply()(implicit context: ActorContext): AppConfig = {
    apply(context.system.settings.config.getConfig("shadowcloud"))
  }
}