package com.karasiq.shadowcloud.serialization.kryo

import scala.language.postfixOps

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.twitter.chill
import com.typesafe.config._

import com.karasiq.shadowcloud.utils.Utils

private[kryo] final class ConfigSerializer(json: Boolean) extends chill.KSerializer[Config](false, true) {
  private[this] val options = if (json) ConfigRenderOptions.concise() else ConfigRenderOptions.defaults()

  def write(kryo: Kryo, output: Output, cfg: Config): Unit = {
    val obj = cfg.root()
    val nonEmpty = !obj.isEmpty
    output.writeBoolean(nonEmpty)
    if (nonEmpty) output.writeString(obj.render(options))
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
