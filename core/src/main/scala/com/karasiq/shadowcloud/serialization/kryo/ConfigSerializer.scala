package com.karasiq.shadowcloud.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.karasiq.shadowcloud.utils.Utils
import com.twitter.chill
import com.typesafe.config._

import scala.language.postfixOps

private[kryo] class ConfigSerializer extends chill.KSerializer[Config](false, true) {
  def write(kryo: Kryo, output: Output, cfg: Config): Unit = {
    val obj = cfg.root()
    val nonEmpty = !obj.isEmpty
    output.writeBoolean(nonEmpty)
    if (nonEmpty) output.writeString(obj.render(ConfigRenderOptions.concise()))
  }

  def read(kryo: Kryo, input: Input, `type`: Class[Config]): Config = {
    if (input.readBoolean()) {
      val configString = input.readString()
      ConfigFactory.parseString(configString)
    } else {
      Utils.emptyConfig
    }
  }
}
