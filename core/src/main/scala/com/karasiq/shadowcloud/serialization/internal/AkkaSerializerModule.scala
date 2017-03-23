package com.karasiq.shadowcloud.serialization.internal

import akka.serialization.{Serializer => AkkaSerializer}
import akka.util.ByteString
import com.karasiq.shadowcloud.serialization.SerializationModule

import scala.language.postfixOps
import scala.reflect.ClassTag

private[serialization] final class AkkaSerializerModule(serializer: AkkaSerializer) extends SerializationModule {
  def toBytes[T <: Serializable](value: T): ByteString = {
    ByteString(serializer.toBinary(value))
  }

  def fromBytes[T <: Serializable : ClassTag](value: ByteString): T = {
    serializer.fromBinary(value.toArray, Some(implicitly[ClassTag[T]].runtimeClass)).asInstanceOf[T]
  }
}
