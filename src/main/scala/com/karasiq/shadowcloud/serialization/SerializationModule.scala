package com.karasiq.shadowcloud.serialization

import akka.util.ByteString

import scala.language.postfixOps

trait SerializationModule {
  def toBytes[T: Manifest](value: T): ByteString
  def fromBytes[T: Manifest](value: ByteString): T
}

object SerializationModule {
  val default: SerializationModule = KryoSerializationModule
}