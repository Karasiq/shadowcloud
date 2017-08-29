package com.karasiq.shadowcloud.config.utils

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.util.ByteString
import com.typesafe.config.ConfigException

import com.karasiq.shadowcloud.utils.Utils
import com.karasiq.shadowcloud.utils.encoding.HexString

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
      } catch { case _: ConfigException ⇒
        Utils.emptyConfig
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
          } catch { case _: ConfigException ⇒
            Utils.emptyConfig
          }
      }
    }

    def getClass[T](path: String): Class[T] = {
      Class.forName(config.getString(path)).asInstanceOf[Class[T]]
    }

    def getBytesInt(path: String): Int = {
      math.min(config.getBytes(path), Int.MaxValue).toInt
    }

    def getStrings(path: String): Seq[String] = {
      import scala.collection.JavaConverters._
      config.getStringList(path).asScala
    }

    def getStringSet(path: String): Set[String] = {
      getStrings(path).toSet
    }

    def getHexString(path: String): ByteString = {
      HexString.decode(config.getString(path))
    }

    def optional[T](value: Config ⇒ T): Option[T] = {
      try {
        Option(value(config))
      } catch { case _: ConfigException ⇒
        None
      }
    }

    def withDefault[T](default: ⇒ T, value: Config ⇒ T): T = {
      optional(value).getOrElse(default)
    }
  }
}
