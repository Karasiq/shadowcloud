package com.karasiq.shadowcloud.serialization.kryo

import akka.util.ByteString
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.twitter.chill

import scala.language.postfixOps

private[kryo] final class ByteStringSerializer extends chill.KSerializer[ByteString](false, true) {
  def write(kryo: Kryo, output: Output, bs: ByteString): Unit = {
    output.writeInt(bs.length, true)
    output.writeBytes(bs.toArray)
  }

  def read(kryo: Kryo, input: Input, cls: Class[ByteString]): ByteString = {
    val length = input.readInt(true)
    val buffer = input.readBytes(length)
    ByteString(buffer)
  }
}