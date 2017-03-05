package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.typesafe.config.{ConfigObject, ConfigValueType}

import scala.collection.JavaConverters._
import scala.language.postfixOps

case class ProvidersConfig[T](providers: Map[String, Class[T]]) {
  def toMap: Map[String, Class[T]] = {
    providers
  }
}

object ProvidersConfig extends ConfigImplicits {
  def apply[T](config: Config): ProvidersConfig[T] = {
    new ProvidersConfig(readProviders[T](config.root()))
  }

  private[this] def readProviders[T](obj: ConfigObject): Map[String, Class[T]] = {
    obj.asScala.toMap.map { case (key, value) ⇒
      require(value.valueType() == ConfigValueType.STRING, s"Invalid provider name: $value")
      key → Class.forName(value.unwrapped().asInstanceOf[String]).asInstanceOf[Class[T]]
    }
  }
}
