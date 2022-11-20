package com.karasiq.shadowcloud.serialization

import scala.reflect.ClassTag

import akka.util.ByteString

trait SerializationModule {
  def toBytes[T <: AnyRef](value: T): ByteString
  def fromBytes[T <: AnyRef: ClassTag](value: ByteString): T
}
