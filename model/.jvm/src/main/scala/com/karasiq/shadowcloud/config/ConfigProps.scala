package com.karasiq.shadowcloud.config

import akka.util.ByteString
import com.karasiq.shadowcloud.utils.Utils
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import scala.collection.JavaConverters._

private[shadowcloud] object ConfigProps {
  private[this] val SupportedFormats = Set(SerializedProps.DefaultFormat, SerializedProps.JsonFormat)

  private[this] val JsonOptions = ConfigRenderOptions.concise()
  private[this] val HoconOptions = {
    ConfigRenderOptions
      .defaults()
      .setOriginComments(false)
      .setJson(false)
  }

  def fromConfig(config: Config, json: Boolean = false): SerializedProps = {
    if (config.entrySet().isEmpty) {
      SerializedProps.empty
    } else {
      val (format, options) = if (json) {
        (SerializedProps.JsonFormat, JsonOptions)
      } else {
        (SerializedProps.DefaultFormat, HoconOptions)
      }

      val configString = config.root().render(options)
      SerializedProps(format, ByteString(configString))
    }
  }

  def fromMap(map: Map[String, _], json: Boolean = false): SerializedProps = {
    fromConfig(ConfigFactory.parseMap(map.asJava), json)
  }

  def fromString(configString: String, json: Boolean = false): SerializedProps = {
    SerializedProps(if (json) SerializedProps.JsonFormat else SerializedProps.DefaultFormat, ByteString(configString))
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
