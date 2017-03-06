package com.karasiq.shadowcloud.config

import akka.util.ByteString
import com.karasiq.shadowcloud.utils.Utils
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

import scala.collection.JavaConverters._
import scala.language.postfixOps

private[shadowcloud] object ConfigProps {
  def fromConfig(config: Config): SerializedProps = {
    if (config.entrySet().size() == 0) {
      SerializedProps.empty
    } else {
      val configString = config.root().render(ConfigRenderOptions.concise())
      SerializedProps("json", ByteString(configString))
    }
  }

  def fromMap(map: Map[String, _]): SerializedProps = {
    fromConfig(ConfigFactory.parseMap(map.asJava))
  }

  def apply(values: (String, _)*): SerializedProps = {
    fromMap(values.toMap)
  }

  def toConfig(props: SerializedProps): Config = {
    if (props.isEmpty) {
      Utils.emptyConfig
    } else {
      require(props.format == "json" || props.format == "hocon")
      ConfigFactory.parseString(props.data.utf8String)
    }
  }
}
