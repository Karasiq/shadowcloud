package com.karasiq.shadowcloud.utils

import scala.util.Try

import com.typesafe.config.Config

private[shadowcloud] trait ProviderInstantiator {
  def getInstance[T](pClass: Class[T]): T
}

private[shadowcloud] object ProviderInstantiator {
  def withConfig[T](className: String, config: Config): T = {
    val pClass = Class.forName(className)
    Try(pClass.getConstructor(classOf[Config]).newInstance(config))
      .getOrElse(pClass.newInstance())
      .asInstanceOf[T]
  }

  def fromConfig[T](config: Config): T = {
    withConfig[T](config.getString("class"), config)
  }
}
