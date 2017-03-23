package com.karasiq.shadowcloud.serialization

object SerializationModules extends AkkaSerializationModules

sealed trait AkkaSerializationModules {
  import akka.actor.ActorSystem
  import akka.serialization.{SerializationExtension => AkkaSerializationExtension, Serializer => AkkaSerializer}
  import com.karasiq.shadowcloud.serialization.internal.{AkkaSerializationExtensionModule, AkkaSerializerModule}

  def fromActorSystem(implicit as: ActorSystem): SerializationModule = {
    new AkkaSerializationExtensionModule(AkkaSerializationExtension(as))
  }

  def fromAkkaSerializer(serializer: AkkaSerializer): SerializationModule = {
    new AkkaSerializerModule(serializer)
  }
}
