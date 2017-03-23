package com.karasiq.shadowcloud.serialization.kryo

import akka.util.ByteString
import com.karasiq.shadowcloud.serialization.SerializationModule
import com.twitter.chill.KryoPool

import scala.language.postfixOps

/**
  * Kryo serialization module
  * @see [[https://github.com/EsotericSoftware/kryo]]
  * @see [[https://github.com/twitter/chill]]
  */
private[serialization] final class KryoSerializationModule extends SerializationModule {
  private[this] val kryoPool = KryoPool.withByteArrayOutputStream(sys.runtime.availableProcessors() * 4, new SCKryoInstantiator)

  def toBytes[T <: Serializable](value: T): ByteString = {
    ByteString(kryoPool.toBytesWithClass(value))
  }

  def fromBytes[T <: Serializable](value: ByteString): T = {
    kryoPool.fromBytes(value.toArray).asInstanceOf[T]
  }
}