package com.karasiq.shadowcloud.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.twitter.chill
import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.{WrappedConfig, WrappedConfigFactory}
import com.karasiq.shadowcloud.serialization.internal.CompanionReflectSerializer

private[kryo] final class WrappedConfigSerializer extends chill.KSerializer[WrappedConfig](false, true) with CompanionReflectSerializer {

  def read(kryo: Kryo, input: Input, `type`: Class[WrappedConfig]): WrappedConfig = {
    val config  = kryo.readObject(input, classOf[Config])
    val factory = getCompanion[WrappedConfigFactory[_ <: WrappedConfig]](`type`)
    factory.apply(config)
  }

  def write(kryo: Kryo, output: Output, `object`: WrappedConfig): Unit = {
    kryo.writeObject(output, `object`.rootConfig)
  }
}
