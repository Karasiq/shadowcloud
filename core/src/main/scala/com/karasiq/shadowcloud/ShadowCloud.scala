package com.karasiq.shadowcloud

import scala.language.postfixOps

import akka.actor.{ActorContext, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.stream.ActorMaterializer

import com.karasiq.shadowcloud.actors.RegionSupervisor
import com.karasiq.shadowcloud.actors.messages.{RegionEnvelope, StorageEnvelope}
import com.karasiq.shadowcloud.actors.utils.StringEventBus
import com.karasiq.shadowcloud.config.{AppConfig, RegionConfig, StorageConfig}
import com.karasiq.shadowcloud.providers.ModuleRegistry
import com.karasiq.shadowcloud.streams.{ChunkProcessingStreams, FileStreams, RegionOps, RegionStreams}

object ShadowCloud extends ExtensionId[ShadowCloudExtension] with ExtensionIdProvider {
  def apply()(implicit context: ActorContext): ShadowCloudExtension = {
    apply(context.system)
  }

  def createExtension(system: ExtendedActorSystem): ShadowCloudExtension = {
    new ShadowCloudExtension(system)
  }

  def lookup(): ExtensionId[_ <: Extension] = {
    ShadowCloud
  }
}

class ShadowCloudExtension(system: ExtendedActorSystem) extends Extension {
  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  object implicits {
    // implicit val actorSystem = system
    implicit val executionContext = system.dispatcher
    implicit val materializer = ActorMaterializer()(system)
  }

  import implicits._

  // -----------------------------------------------------------------------
  // Configuration
  // -----------------------------------------------------------------------
  val rootConfig = system.settings.config.getConfig("shadowcloud")
  val config = AppConfig(rootConfig)
  val modules = ModuleRegistry(config)

  def regionConfig(regionId: String): RegionConfig = {
    RegionConfig.forId(regionId, rootConfig)
  }

  def storageConfig(storageId: String): StorageConfig = {
    StorageConfig.forId(storageId, rootConfig)
  }

  // -----------------------------------------------------------------------
  // Actors
  // -----------------------------------------------------------------------
  object actors {
    val regionSupervisor = system.actorOf(RegionSupervisor.props, "shadowcloud")
  }

  // -----------------------------------------------------------------------
  // Events
  // -----------------------------------------------------------------------
  object eventStreams {
    val region = new StringEventBus[RegionEnvelope](_.regionId)
    val storage = new StringEventBus[StorageEnvelope](_.storageId)

    def publishRegionEvent(regionId: String, event: Any): Unit = {
      region.publish(RegionEnvelope(regionId, event))
    }

    def publishStorageEvent(storageId: String, event: Any): Unit = {
      storage.publish(StorageEnvelope(storageId, event))
    }
  }

  // -----------------------------------------------------------------------
  // Streams
  // -----------------------------------------------------------------------
  object streams {
    val chunk = ChunkProcessingStreams(config)
    val region = RegionStreams(actors.regionSupervisor, config.parallelism)
    val file = FileStreams(region, chunk)
  }

  object ops {
    // TODO: Region supervisor ops
    val region = RegionOps(actors.regionSupervisor)
  }
}
