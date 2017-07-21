package com.karasiq.shadowcloud

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{ActorContext, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout

import com.karasiq.shadowcloud.actors.RegionSupervisor
import com.karasiq.shadowcloud.actors.messages.{RegionEnvelope, StorageEnvelope}
import com.karasiq.shadowcloud.actors.utils.StringEventBus
import com.karasiq.shadowcloud.config.{RegionConfig, SCConfig, StorageConfig}
import com.karasiq.shadowcloud.config.keys.KeySet
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.providers.SCModules
import com.karasiq.shadowcloud.serialization.SerializationModules
import com.karasiq.shadowcloud.streams._
import com.karasiq.shadowcloud.utils.{ProviderInstantiator, SCProviderInstantiator}

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

class ShadowCloudExtension(_actorSystem: ExtendedActorSystem) extends Extension {
  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  object implicits {
    implicit val actorSystem: ActorSystem = _actorSystem
    implicit val executionContext: ExecutionContext = _actorSystem.dispatcher
    implicit val materializer: Materializer = ActorMaterializer()(_actorSystem)
    implicit val defaultTimeout: Timeout = Timeout(5 seconds)
    private[ShadowCloudExtension] implicit val pInst: ProviderInstantiator = new SCProviderInstantiator(ShadowCloudExtension.this)
  }

  import implicits._

  // -----------------------------------------------------------------------
  // Configuration
  // -----------------------------------------------------------------------

  val rootConfig = actorSystem.settings.config.getConfig("shadowcloud")
  val config = SCConfig(rootConfig)
  val modules = SCModules(config)
  val serialization = SerializationModules.forActorSystem(actorSystem)

  def regionConfig(regionId: String): RegionConfig = {
    RegionConfig.forId(regionId, rootConfig)
  }

  def storageConfig(storageId: String): StorageConfig = {
    StorageConfig.forId(storageId, rootConfig)
  }

  object keys {
    val provider = pInst.getInstance(config.crypto.keyProvider)

    def generateKeySet(): KeySet = {
      val enc = modules.crypto.encryptionModule(config.crypto.encryption.keys).createParameters()
      val sign = modules.crypto.signModule(config.crypto.signing.index).createParameters()
      KeySet(UUID.randomUUID(), sign, enc)
    }
  }

  object passwords extends ConfigImplicits {
    val provider = pInst.getInstance(config.crypto.passwordProvider)

    def getOrAsk(configPath: String, passwordId: String): String = {
      rootConfig.withDefault(provider.askPassword(passwordId), _.getString(configPath))
    }

    lazy val masterPassword: String = {
      getOrAsk("password", "master")
    }
  }

  // -----------------------------------------------------------------------
  // Actors
  // -----------------------------------------------------------------------
  object actors {
    val regionSupervisor = _actorSystem.actorOf(RegionSupervisor.props, "shadowcloud")
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
    val index = IndexProcessingStreams(modules, config, keys.provider)
    val region = RegionStreams(actors.regionSupervisor, config.parallelism)
    val file = FileStreams(region, chunk)
  }

  object ops {
    val supervisor = RegionSupervisorOps(actors.regionSupervisor)
    val region = RegionOps(actors.regionSupervisor)
    val storage = StorageOps(actors.regionSupervisor)
    val background = BackgroundOps(ShadowCloudExtension.this)
  }
}
