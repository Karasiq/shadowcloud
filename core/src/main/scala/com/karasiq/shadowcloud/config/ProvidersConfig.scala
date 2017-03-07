package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.typesafe.config.{ConfigObject, ConfigValueType}

import scala.collection.JavaConverters._
import scala.language.{implicitConversions, postfixOps}

case class ProvidersConfig[T](classes: Seq[(String, Class[T])]) extends AnyVal {
  def instances: Seq[(String, T)] = {
    classes.map { case (name, pClass) ⇒
      name → pClass.newInstance()
    }
  }
}

object ProvidersConfig extends ConfigImplicits {
  def apply[T](config: Config): ProvidersConfig[T] = {
    new ProvidersConfig(readProviders[T](config.root()))
  }

  private[this] def readProviders[T](obj: ConfigObject): Seq[(String, Class[T])] = {
    obj.asScala.toVector.map { case (key, value) ⇒
      require(value.valueType() == ConfigValueType.STRING, s"Invalid provider name: $value")
      key → Class.forName(value.unwrapped().asInstanceOf[String]).asInstanceOf[Class[T]]
    }
  }
}
