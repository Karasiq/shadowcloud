package com.karasiq.shadowcloud.serialization

import akka.stream.scaladsl.Flow
import akka.util.ByteString

import scala.language.postfixOps

object Serialization {
  def toBytes[T: Manifest](module: SerializationModule = SerializationModule.default) = {
    Flow[T].map(value ⇒ module.toBytes(value))
  }

  def fromBytes[T: Manifest](module: SerializationModule = SerializationModule.default) = {
    Flow[ByteString].map(bytes ⇒ module.fromBytes(bytes))
  }
}
