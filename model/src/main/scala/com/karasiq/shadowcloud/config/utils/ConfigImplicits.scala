package com.karasiq.shadowcloud.config.utils

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{ActorContext, ActorSystem}
import com.typesafe.config.{ConfigException, ConfigFactory}

import com.karasiq.shadowcloud.utils.Utils

object ConfigImplicits extends ConfigImplicits

trait ConfigImplicits {
  protected type Config = com.typesafe.config.Config

  def actorContextConfig(implicit ac: ActorContext): Config = {
    ac.system.settings.config
  }

  def actorContextConfig(path: String)(implicit ac: ActorContext): Config = {
    actorContextConfig.getConfig(path)
  }

  def actorSystemConfig(implicit as: ActorSystem): Config = {
    as.settings.config
  }

  def actorSystemConfig(path: String)(implicit as: ActorSystem): Config = {
    actorSystemConfig.getConfig(path)
  }

  protected implicit class ConfigOps(config: Config) {
    def getFiniteDuration(path: String): FiniteDuration = {
      Utils.toScalaDuration(config.getDuration(path))
    }

    def getConfigIfExists(path: String): Config = {
      try {
        config.getConfig(path)
      } catch { case _: ConfigException ⇒
        ConfigFactory.empty()
      }
    }

    def getConfigOrRef(path: String): Config = {
      try {
        config.getConfig(path)
      } catch { case _: ConfigException ⇒
        try {
          val path1 = config.getString(path)
          // getConfigOrRefRec(path1)
          config.getConfig(path1)
        } catch { case _: ConfigException ⇒
          ConfigFactory.empty()
        }
      }
    }

    @inline 
    def withDefault[T](default: T, value: Config ⇒ T): T = {
      try {
        value(config)
      } catch { case _: ConfigException ⇒
        default
      }
    }
  }
}
