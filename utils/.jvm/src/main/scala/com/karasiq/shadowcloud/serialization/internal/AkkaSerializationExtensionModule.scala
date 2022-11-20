package com.karasiq.shadowcloud.serialization.internal

import akka.serialization.{Serialization ⇒ AkkaSerialization}
import akka.util.ByteString
import com.karasiq.shadowcloud.serialization.SerializationModule
import com.karasiq.shadowcloud.utils.ByteStringUnsafe

import scala.reflect.ClassTag

private[serialization] final class AkkaSerializationExtensionModule(extension: AkkaSerialization) extends SerializationModule {
  def toBytes[T <: AnyRef](value: T): ByteString = {
    extension.serialize(value).map(ByteString.fromArrayUnsafe).get
  }

  def fromBytes[T <: AnyRef: ClassTag](value: ByteString): T = {
    extension.deserialize(ByteStringUnsafe.getArray(value), implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]).get
  }
}
