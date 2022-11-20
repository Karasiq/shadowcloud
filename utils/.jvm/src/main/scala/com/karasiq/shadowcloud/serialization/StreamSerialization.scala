package com.karasiq.shadowcloud.serialization

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Framing}
import akka.util.ByteString

import scala.reflect.ClassTag

object StreamSerialization {
  def apply(module: SerializationModule): StreamSerialization = {
    new StreamSerialization(module)
  }

  def frame(frameLimit: Int): Flow[ByteString, ByteString, NotUsed] = {
    Framing.simpleFramingProtocolEncoder(frameLimit)
  }

  def deframe(frameLimit: Int): Flow[ByteString, ByteString, NotUsed] = {
    Framing.simpleFramingProtocolDecoder(frameLimit)
  }

  def serializeFramed[T <: AnyRef: ClassTag](serialization: SerializationModule, frameLimit: Int): Flow[T, ByteString, NotUsed] = {
    Flow[T]
      .via(StreamSerialization(serialization).toBytes)
      // .filter(_.length <= frameLimit)
      .via(frame(frameLimit))
      .named("framedSerialize")
  }

  def deserializeFramed[T <: AnyRef: ClassTag](serialization: SerializationModule, frameLimit: Int): Flow[ByteString, T, NotUsed] = {
    Flow[ByteString]
      .via(deframe(frameLimit))
      .via(StreamSerialization(serialization).fromBytes[T])
      .named("framedDeserialize")
  }
}

final class StreamSerialization(private val module: SerializationModule) extends AnyVal {
  def toBytes[T <: AnyRef]: Flow[T, ByteString, NotUsed] = {
    Flow[T]
      .map(module.toBytes[T])
      .named("toBytes")
  }

  def fromBytes[T <: AnyRef: ClassTag]: Flow[ByteString, T, NotUsed] = {
    Flow[ByteString]
      .map(module.fromBytes[T])
      .named("fromBytes")
  }
}
