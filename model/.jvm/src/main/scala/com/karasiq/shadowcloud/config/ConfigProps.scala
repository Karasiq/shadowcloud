package com.karasiq.shadowcloud.config

import scala.collection.JavaConverters._
import scala.language.postfixOps

import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import com.karasiq.shadowcloud.utils.Utils

private[shadowcloud] object ConfigProps {
  private[this] val supportedFormats = Set("json", "hocon")

  def fromConfig(config: Config, json: Boolean = true): SerializedProps = {
    if (config.entrySet().size() == 0) {
      SerializedProps.empty
    } else {
      val (format, options) = if (json) {
        ("json", ConfigRenderOptions.concise())
      } else {
        ("hocon", ConfigRenderOptions.defaults())
      }

      val configString = config.root().render(options)
      SerializedProps(format, ByteString(configString))
    }
  }

  def fromMap(map: Map[String, _]): SerializedProps = {
    fromConfig(ConfigFactory.parseMap(map.asJava))
  }

  def apply(values: (String, _)*): SerializedProps = {
    fromMap(values.toMap)
  }

  def toConfig(props: SerializedProps): Config = {
    if (props.nonEmpty && supportedFormats.contains(props.format)) {
      ConfigFactory.parseString(props.data.utf8String)
    } else {
      Utils.emptyConfig
    }
  }
}
