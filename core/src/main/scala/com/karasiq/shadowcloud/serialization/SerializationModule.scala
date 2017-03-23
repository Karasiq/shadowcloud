package com.karasiq.shadowcloud.serialization

import akka.util.ByteString
import com.karasiq.shadowcloud.serialization.kryo.KryoSerializationModule

import scala.language.postfixOps

trait SerializationModule {
  type Serializable = Any
  def toBytes[T <: Serializable](value: T): ByteString
  def fromBytes[T <: Serializable](value: ByteString): T
}

object SerializationModule {
  val kryo: SerializationModule = new KryoSerializationModule
  val default = kryo
}