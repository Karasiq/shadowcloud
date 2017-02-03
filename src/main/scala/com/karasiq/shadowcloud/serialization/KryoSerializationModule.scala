package com.karasiq.shadowcloud.serialization

import akka.util.ByteString
import com.twitter.chill.{KryoPool, ScalaKryoInstantiator}

import scala.language.postfixOps

/**
  * Kryo serialization module
  * @see [[https://github.com/EsotericSoftware/kryo]]
  * @see [[https://github.com/twitter/chill]]
  */
class KryoSerializationModule extends SerializationModule {
  protected val kryoPool = KryoPool.withByteArrayOutputStream(Runtime.getRuntime.availableProcessors() * 2, new ScalaKryoInstantiator())

  def toBytes[T: Manifest](value: T) = {
    ByteString(kryoPool.toBytesWithoutClass(value))
  }

  def fromBytes[T: Manifest](value: ByteString) = {
    kryoPool.fromBytes(value.toArray, implicitly[Manifest[T]].runtimeClass).asInstanceOf[T]
  }
}

object KryoSerializationModule extends KryoSerializationModule