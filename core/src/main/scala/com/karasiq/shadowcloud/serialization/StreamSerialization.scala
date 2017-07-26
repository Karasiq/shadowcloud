package com.karasiq.shadowcloud.serialization

import scala.language.postfixOps
import scala.reflect.ClassTag

import akka.NotUsed
import akka.stream.scaladsl.{Compression, Flow, Framing}
import akka.util.ByteString

object StreamSerialization {
  def apply(module: SerializationModule): StreamSerialization = {
    new StreamSerialization(module)
  }

  def serializeFramed[T <: AnyRef : ClassTag](serialization: SerializationModule,
                                              frameLimit: Int): Flow[T, ByteString, NotUsed] = {
    Flow[T]
      .via(StreamSerialization(serialization).toBytes)
      .filter(_.length <= frameLimit)
      .via(Framing.simpleFramingProtocolEncoder(frameLimit))
      .named("framedSerialize")
  }

  def deserializeFramed[T <: AnyRef : ClassTag](serialization: SerializationModule,
                                                frameLimit: Int): Flow[ByteString, T, NotUsed] = {
    Flow[ByteString]
      .via(Framing.simpleFramingProtocolDecoder(frameLimit))
      .via(StreamSerialization(serialization).fromBytes[T])
      .named("framedDeserialize")
  }

  def serializeFramedGzip[T <: AnyRef : ClassTag](serialization: SerializationModule,
                                                  frameLimit: Int): Flow[T, ByteString, NotUsed] = {
    Flow[T]
      .via(serializeFramed(serialization, frameLimit))
      .via(Compression.gzip)
      .named("gzipFramedSerialize")
  }

  def deserializeFramedGzip[T <: AnyRef : ClassTag](serialization: SerializationModule,
                                                    frameLimit: Int): Flow[ByteString, T, NotUsed] = {
    Flow[ByteString]
      .via(Compression.gunzip())
      .via(deserializeFramed(serialization, frameLimit))
      .named("gzipFramedDeserialize")
  }
}

final class StreamSerialization(private val module: SerializationModule) extends AnyVal {
  def toBytes[T <: AnyRef]: Flow[T, ByteString, NotUsed] = {
    Flow[T]
      .map(module.toBytes[T])
      .named("toBytes")
  }

  def fromBytes[T <: AnyRef : ClassTag]: Flow[ByteString, T, NotUsed] = {
    Flow[ByteString]
      .map(module.fromBytes[T])
      .named("fromBytes")
  }
}
