package com.karasiq.shadowcloud.serialization.kryo

import scala.language.postfixOps

import akka.util.ByteString
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.twitter.chill
import com.typesafe.config._

import com.karasiq.shadowcloud.config.{ConfigProps, SerializedProps}
import com.karasiq.shadowcloud.utils.Utils

private[kryo] final class ConfigSerializer(json: Boolean) extends chill.KSerializer[Config](false, true) {
  def write(kryo: Kryo, output: Output, config: Config): Unit = {
    val serialized = ConfigProps.fromConfig(config, json)
    output.writeBoolean(serialized.nonEmpty)
    if (serialized.nonEmpty) output.writeString(serialized.data.utf8String)
  }

  def read(kryo: Kryo, input: Input, `type`: Class[Config]): Config = {
    val isNotEmpty = input.readBoolean()
    if (isNotEmpty) {
      val configString = input.readString()
      val serialized = SerializedProps(if (json) "json" else "hocon", ByteString(configString))
      ConfigProps.toConfig(serialized)
    } else {
      Utils.emptyConfig
    }
  }
}
