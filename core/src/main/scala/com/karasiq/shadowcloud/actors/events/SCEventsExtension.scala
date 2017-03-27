package com.karasiq.shadowcloud.actors.events

import akka.actor.{ActorContext, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.karasiq.shadowcloud.actors.internal.StringEventBus
import com.karasiq.shadowcloud.actors.messages.{RegionEnvelope, StorageEnvelope}

object SCEvents extends ExtensionId[SCEventsExtension] with ExtensionIdProvider {
  def apply()(implicit context: ActorContext): SCEventsExtension = {
    apply(context.system)
  }

  def createExtension(system: ExtendedActorSystem): SCEventsExtension = {
    new SCEventsExtension
  }

  def lookup(): ExtensionId[_ <: Extension] = {
    SCEvents
  }
}

final class SCEventsExtension extends Extension {
  val region = new StringEventBus[RegionEnvelope](_.regionId)
  val storage = new StringEventBus[StorageEnvelope](_.storageId)
}             
