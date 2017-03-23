package com.karasiq.shadowcloud.serialization.utils

import akka.actor.ExtendedActorSystem
import akka.serialization.Serializer
import akka.util.ByteString
import com.karasiq.shadowcloud.serialization.SerializationModule

import scala.language.postfixOps

private[serialization] final class AkkaSerializationAdapter(system: ExtendedActorSystem) extends Serializer {
  val serializationModule = createModule()
  def createModule(): SerializationModule = SerializationModule.default
  def identifier: Int = 924816
  def includeManifest: Boolean = false

  def toBinary(o: AnyRef): Array[Byte] = {
    serializationModule.toBytes(o).toArray
  }

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    serializationModule.fromBytes(ByteString(bytes))
  }
}
