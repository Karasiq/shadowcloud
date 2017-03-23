package com.karasiq.shadowcloud.serialization

import akka.util.ByteString

import scala.language.postfixOps
import scala.reflect.ClassTag

trait SerializationModule {
  type Serializable = AnyRef
  def toBytes[T <: Serializable](value: T): ByteString
  def fromBytes[T <: Serializable : ClassTag](value: ByteString): T
}