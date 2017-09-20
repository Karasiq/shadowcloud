package com.karasiq.shadowcloud.config

import scala.collection.JavaConverters._
import scala.language.postfixOps

import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import com.karasiq.shadowcloud.utils.Utils

private[shadowcloud] object ConfigProps {
  private[this] val SupportedFormats = Set("json", "hocon")
  
  private[this] val JSONOptions = ConfigRenderOptions.concise()
  private[this] val HOCONOptions = {
    ConfigRenderOptions.defaults()
      .setOriginComments(false)
      .setJson(false)
  }

  def fromConfig(config: Config, json: Boolean = false): SerializedProps = {
    if (config.entrySet().isEmpty) {
      SerializedProps.empty
    } else {
      val (format, options) = if (json) {
        ("json", JSONOptions)
      } else {
        ("hocon", HOCONOptions)
      }

      val configString = config.root().render(options)
      SerializedProps(format, ByteString(configString))
    }
  }

  def fromMap(map: Map[String, _], json: Boolean = false): SerializedProps = {
    fromConfig(ConfigFactory.parseMap(map.asJava), json)
  }

  def fromString(configString: String, json: Boolean = false): SerializedProps = {
    SerializedProps(if (json) "json" else "hocon", ByteString(configString))
  }

  def apply(values: (String, _)*): SerializedProps = {
    fromMap(values.toMap)
  }

  def toConfig(props: SerializedProps): Config = {
    if (props.nonEmpty && SupportedFormats.contains(props.format)) {
      ConfigFactory.parseString(props.data.utf8String)
    } else {
      Utils.emptyConfig
    }
  }

  def reformat(props: SerializedProps, json: Boolean = false): SerializedProps = {
    fromConfig(toConfig(props), json)
  }
}
