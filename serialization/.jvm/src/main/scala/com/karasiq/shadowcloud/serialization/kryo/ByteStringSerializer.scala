package com.karasiq.shadowcloud.serialization.kryo

import scala.language.postfixOps

import akka.util.ByteString
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.twitter.chill

private[kryo] final class ByteStringSerializer extends chill.KSerializer[ByteString](false, true) {
  def write(kryo: Kryo, output: Output, bs: ByteString): Unit = {
    val length = bs.length
    output.writeInt(length, true)
    if (length != 0) output.writeBytes(bs.toArray)
  }

  def read(kryo: Kryo, input: Input, cls: Class[ByteString]): ByteString = {
    val length = input.readInt(true)
    if (length != 0) {
      val buffer = input.readBytes(length)
      ByteString.fromArrayUnsafe(buffer)
    } else {
      ByteString.empty
    }
  }
}