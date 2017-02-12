package com.karasiq.shadowcloud.serialization.kryo

import akka.util.ByteString
import com.karasiq.shadowcloud.serialization.SerializationModule
import com.twitter.chill.{KryoBase, KryoPool, ScalaKryoInstantiator}

import scala.language.postfixOps
import scala.reflect.ClassTag

/**
  * Kryo serialization module
  * @see [[https://github.com/EsotericSoftware/kryo]]
  * @see [[https://github.com/twitter/chill]]
  */
class KryoSerializationModule extends SerializationModule {
  private[this] val instantiator = new ScalaKryoInstantiator() {
    override def newKryo(): KryoBase = {
      val kryo = super.newKryo()
      kryo.register(classOf[ByteString], new ByteStringSerializer)
      kryo
    }
  }
  private[this] val kryoPool = KryoPool.withByteArrayOutputStream(sys.runtime.availableProcessors(), instantiator)

  def toBytes[T: ClassTag](value: T): ByteString = {
    ByteString(kryoPool.toBytesWithoutClass(value))
  }

  def fromBytes[T: ClassTag](value: ByteString): T = {
    kryoPool.fromBytes(value.toArray, implicitly[ClassTag[T]].runtimeClass).asInstanceOf[T]
  }
}

object KryoSerializationModule extends KryoSerializationModule