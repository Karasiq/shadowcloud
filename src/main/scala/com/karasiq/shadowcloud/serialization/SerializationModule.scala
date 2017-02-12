package com.karasiq.shadowcloud.serialization

import akka.util.ByteString
import com.karasiq.shadowcloud.serialization.kryo.KryoSerializationModule

import scala.language.postfixOps
import scala.reflect.ClassTag

trait SerializationModule {
  def toBytes[T: ClassTag](value: T): ByteString
  def fromBytes[T: ClassTag](value: ByteString): T
}

object SerializationModule {
  val default: SerializationModule = KryoSerializationModule
}