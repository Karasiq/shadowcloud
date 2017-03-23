package com.karasiq.shadowcloud.serialization

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import scala.language.postfixOps
import scala.reflect.ClassTag

object Serialization {
  def toBytes[T](module: SerializationModule = SerializationModule.default): Flow[T, ByteString, NotUsed] = {
    Flow[T].map(value ⇒ module.toBytes(value))
  }

  def fromBytes[T: ClassTag](module: SerializationModule = SerializationModule.default): Flow[ByteString, T, NotUsed] = {
    Flow[ByteString].map(bytes ⇒ module.fromBytes[T](bytes))
  }
}
