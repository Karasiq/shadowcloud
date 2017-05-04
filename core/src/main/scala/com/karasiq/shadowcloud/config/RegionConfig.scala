package com.karasiq.shadowcloud.config

import akka.actor.ActorContext
import com.typesafe.config.ConfigFactory

import com.karasiq.shadowcloud.config.utils.ConfigImplicits

case class RegionConfig(dataReplicationFactor: Int, indexReplicationFactor: Int)

object RegionConfig extends ConfigImplicits {
  private[this] def withFallback(regionId: String, regionConfig: Config, rootConfig: Config): RegionConfig = {
    apply(regionConfig
      .withFallback(rootConfig.getConfigOrRef(s"regions.$regionId"))
      .withFallback(rootConfig.getConfig("default-region")))
  }


  def fromConfig(regionId: String, config: Config): RegionConfig = {
    withFallback(regionId, ConfigFactory.empty(), config)
  }

  def apply(regionId: String, regionConfig: Config)(implicit ac: ActorContext): RegionConfig = {
    withFallback(regionId, regionConfig, actorContextConfig(AppConfig.ROOT_CFG_PATH))
  }

  def apply(regionId: String)(implicit ac: ActorContext): RegionConfig = {
    fromConfig(regionId, actorContextConfig(AppConfig.ROOT_CFG_PATH))
  }

  def apply(config: Config): RegionConfig = {
    RegionConfig(
      config.getInt("data-replication-factor"),
      config.getInt("index-replication-factor")
    )
  }
}