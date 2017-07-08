package com.karasiq.shadowcloud.config.utils

import scala.concurrent.duration._
import scala.language.postfixOps

import com.typesafe.config.{ConfigException, ConfigFactory}

import com.karasiq.shadowcloud.utils.Utils

object ConfigImplicits extends ConfigImplicits

trait ConfigImplicits {
  type Config = com.typesafe.config.Config

  implicit class ConfigOps(config: Config) {
    def getFiniteDuration(path: String): FiniteDuration = {
      Utils.toScalaDuration(config.getDuration(path))
    }

    def getConfigIfExists(path: String): Config = {
      try {
        config.getConfig(path)
      } catch {
        case _: ConfigException ⇒
          ConfigFactory.empty()
      }
    }

    def getConfigOrRef(path: String): Config = {
      try {
        config.getConfig(path)
      } catch {
        case _: ConfigException ⇒
          try {
            val path1 = config.getString(path)
            // getConfigOrRefRec(path1)
            config.getConfig(path1)
          } catch {
            case _: ConfigException ⇒
              ConfigFactory.empty()
          }
      }
    }

    def getClass[T](path: String): Class[T] = {
      Class.forName(config.getString(path)).asInstanceOf[Class[T]]
    }

    def getBytesInt(path: String): Int = {
      math.min(config.getBytes(path), Int.MaxValue).toInt
    }

    @inline
    def withDefault[T](default: ⇒ T, value: Config ⇒ T): T = {
      try {
        value(config)
      } catch {
        case _: ConfigException ⇒
          default
      }
    }
  }
}
