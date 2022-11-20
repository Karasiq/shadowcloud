package com.karasiq.shadowcloud.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import com.twitter.chill

import com.karasiq.shadowcloud.serialization.internal.CompanionReflectSerializer

// ScalaPB serializer
private[kryo] final class GeneratedMessageSerializer extends chill.KSerializer[GeneratedMessage](false, true) with CompanionReflectSerializer {

  def read(kryo: Kryo, input: Input, `type`: Class[GeneratedMessage]): GeneratedMessage = {
    val bytes     = kryo.readObject(input, classOf[Array[Byte]])
    val companion = getCompanion[GeneratedMessageCompanion[_ <: GeneratedMessage]](`type`)
    companion.parseFrom(bytes)
  }

  def write(kryo: Kryo, output: Output, `object`: GeneratedMessage): Unit = {
    kryo.writeObject(output, `object`.toByteArray)
  }
}
