package com.karasiq.shadowcloud.config

import akka.util.ByteString
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.utils.Utils
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

case class SerializedProps(format: String = "", data: ByteString = ByteString.empty) extends HasEmpty {
  def isEmpty: Boolean = {
    data.isEmpty
  }

  override def toString: String = {
    if (isEmpty) {
      "SerializedProps.empty"
    } else {
      s"SerializedProps($format, ${data.length} bytes)"
    }
  }
}

object SerializedProps {
  val empty = SerializedProps()

  def fromConfig(config: Config): SerializedProps = {
    if (config.entrySet().size() == 0) {
      SerializedProps.empty
    } else {
      val configString = config.root().render(ConfigRenderOptions.concise())
      SerializedProps("json", ByteString(configString))
    }
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