package com.karasiq.shadowcloud.serialization

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import scala.language.postfixOps
import scala.reflect.ClassTag

object StreamSerialization {
  def apply(module: SerializationModule): StreamSerialization = {
    new StreamSerialization(module)
  }
}

final class StreamSerialization(private val module: SerializationModule) extends AnyVal {
  def toBytes[T <: module.Serializable]: Flow[T, ByteString, NotUsed] = {
    Flow[T].map(module.toBytes[T])
  }

  def fromBytes[T <: module.Serializable : ClassTag]: Flow[ByteString, T, NotUsed] = {
    Flow[ByteString].map(module.fromBytes[T])
  }
}
