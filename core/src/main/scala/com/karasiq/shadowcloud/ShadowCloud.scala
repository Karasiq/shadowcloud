package com.karasiq.shadowcloud

import java.util.UUID

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{ActorContext, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.stream.ActorMaterializer
import akka.util.Timeout

import com.karasiq.shadowcloud.actors.RegionSupervisor
import com.karasiq.shadowcloud.actors.messages.{RegionEnvelope, StorageEnvelope}
import com.karasiq.shadowcloud.actors.utils.StringEventBus
import com.karasiq.shadowcloud.config.{AppConfig, RegionConfig, StorageConfig}
import com.karasiq.shadowcloud.config.keys.KeySet
import com.karasiq.shadowcloud.providers.ModuleRegistry
import com.karasiq.shadowcloud.serialization.SerializationModules
import com.karasiq.shadowcloud.streams._
import com.karasiq.shadowcloud.utils.AppPasswordProvider

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
    implicit val actorSystem = system
    implicit val executionContext = system.dispatcher
    implicit val materializer = ActorMaterializer()(system)
    implicit val defaultTimeout = Timeout(5 seconds)
  }

  import implicits._

  // -----------------------------------------------------------------------
  // Configuration
  // -----------------------------------------------------------------------
  val rootConfig = system.settings.config.getConfig("shadowcloud")
  val config = AppConfig(rootConfig)
  val modules = ModuleRegistry(config)
  val serialization = SerializationModules.fromActorSystem(system)
  val password = new AppPasswordProvider(rootConfig)

  def regionConfig(regionId: String): RegionConfig = {
    RegionConfig.forId(regionId, rootConfig)
  }

  def storageConfig(storageId: String): StorageConfig = {
    StorageConfig.forId(storageId, rootConfig)
  }

  object keys {
    def generateKeySet(): KeySet = {
      val enc = modules.encryptionModule(config.crypto.encryption.keys).createParameters()
      val sign = modules.signModule(config.crypto.signing.index).createParameters()
      KeySet(UUID.randomUUID(), sign, enc)
    }

    val provider = config.crypto.keyProvider.getConstructor(classOf[ShadowCloudExtension]).newInstance(ShadowCloudExtension.this)
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
    val index = IndexProcessingStreams(modules, config, keys.provider)(system)
    val region = RegionStreams(actors.regionSupervisor, config.parallelism)
    val file = FileStreams(region, chunk)
  }

  object ops {
    val supervisor = RegionSupervisorOps(actors.regionSupervisor)
    val region = RegionOps(actors.regionSupervisor)
    val storage = StorageOps(actors.regionSupervisor)
  }
}
