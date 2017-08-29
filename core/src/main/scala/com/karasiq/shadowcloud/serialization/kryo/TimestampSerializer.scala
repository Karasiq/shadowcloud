package com.karasiq.shadowcloud.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.twitter.chill

import com.karasiq.shadowcloud.model.Timestamp

private[kryo] final class TimestampSerializer extends chill.KSerializer[Timestamp](false, true) {
  def read(kryo: Kryo, input: Input, `type`: Class[Timestamp]): Timestamp = {
    val created = input.readLong(true)
    val modifiedOffset = input.readLong(true)
    Timestamp(created, created + modifiedOffset)
  }

  def write(kryo: Kryo, output: Output, timestamp: Timestamp): Unit = {
    val modifiedOffset = timestamp.lastModified - timestamp.created // math.max(0L, timestamp.lastModified - timestamp.created)
    output.writeLong(timestamp.created, true)
    output.writeLong(modifiedOffset, true)
  }
}