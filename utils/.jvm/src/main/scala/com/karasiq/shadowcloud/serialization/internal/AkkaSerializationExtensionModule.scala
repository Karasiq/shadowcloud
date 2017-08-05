package com.karasiq.shadowcloud.serialization.internal

import scala.language.postfixOps
import scala.reflect.ClassTag

import akka.serialization.{Serialization ⇒ AkkaSerialization}
import akka.util.ByteString

import com.karasiq.shadowcloud.serialization.SerializationModule

private[serialization] final class AkkaSerializationExtensionModule(extension: AkkaSerialization) extends SerializationModule {
  def toBytes[T <: AnyRef](value: T): ByteString = {
    extension.serialize(value).map(ByteString(_)).get
  }

  def fromBytes[T <: AnyRef : ClassTag](value: ByteString): T = {
    extension.deserialize(value.toArray, implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]).get
  }
}
