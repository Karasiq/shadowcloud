package com.karasiq.shadowcloud.serialization.internal

import akka.serialization.{Serializer ⇒ AkkaSerializer}
import akka.util.ByteString
import com.karasiq.shadowcloud.serialization.SerializationModule
import com.karasiq.shadowcloud.utils.ByteStringUnsafe

import scala.reflect.ClassTag

private[serialization] final class AkkaSerializerModule(serializer: AkkaSerializer) extends SerializationModule {
  def toBytes[T <: AnyRef](value: T): ByteString = {
    ByteString.fromArrayUnsafe(serializer.toBinary(value))
  }

  def fromBytes[T <: AnyRef: ClassTag](value: ByteString): T = {
    serializer.fromBinary(ByteStringUnsafe.getArray(value), Some(implicitly[ClassTag[T]].runtimeClass)).asInstanceOf[T]
  }
}
