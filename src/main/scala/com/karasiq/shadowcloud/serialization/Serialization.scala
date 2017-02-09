package com.karasiq.shadowcloud.serialization

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import scala.language.postfixOps

object Serialization {
  def toBytes[T: Manifest](module: SerializationModule = SerializationModule.default): Flow[T, ByteString, NotUsed] = {
    Flow[T].map(value ⇒ module.toBytes(value))
  }

  def fromBytes[T: Manifest](module: SerializationModule = SerializationModule.default): Flow[ByteString, T, NotUsed] = {
    Flow[ByteString].map(bytes ⇒ module.fromBytes[T](bytes))
  }
}
