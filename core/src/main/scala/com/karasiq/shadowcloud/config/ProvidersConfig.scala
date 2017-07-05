package com.karasiq.shadowcloud.config

import scala.collection.JavaConverters._
import scala.language.{implicitConversions, postfixOps}

import com.typesafe.config.{Config, ConfigObject, ConfigValueType}

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.utils.ProviderInstantiator

private[shadowcloud] case class ProvidersConfig[T](rootConfig: Config, classes: Seq[(String, Class[T])]) extends WrappedConfig {
  def instances(implicit inst: ProviderInstantiator): Seq[(String, T)] = {
    classes.map { case (name, pClass) ⇒
      name → inst.getInstance(pClass)
    }
  }
}

private[shadowcloud] object ProvidersConfig extends WrappedConfigFactory[ProvidersConfig[_]] with ConfigImplicits {
  def withType[T](config: Config): ProvidersConfig[T] = {
    ProvidersConfig(config, readProviders[T](config.root()))
  }

  def apply(config: Config): ProvidersConfig[_] = {
    withType[Any](config)
  }

  private[this] def readProviders[T](obj: ConfigObject): Seq[(String, Class[T])] = {
    obj.asScala.toVector.map { case (key, value) ⇒
      require(value.valueType() == ConfigValueType.STRING, s"Invalid provider name: $value")
      key → Class.forName(value.unwrapped().asInstanceOf[String]).asInstanceOf[Class[T]]
    }
  }
}
