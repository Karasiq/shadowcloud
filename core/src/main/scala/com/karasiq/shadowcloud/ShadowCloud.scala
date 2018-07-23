package com.karasiq.shadowcloud

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.reflect.ClassTag

import akka.Done
import akka.actor.{ActorContext, ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.actors.{RegionSupervisor, SCDispatchers}
import com.karasiq.shadowcloud.actors.messages.{RegionEnvelope, StorageEnvelope}
import com.karasiq.shadowcloud.actors.utils.StringEventBus
import com.karasiq.shadowcloud.cache.CacheProvider
import com.karasiq.shadowcloud.config._
import com.karasiq.shadowcloud.config.passwords.PasswordProvider
import com.karasiq.shadowcloud.model.{RegionId, StorageId}
import com.karasiq.shadowcloud.model.crypto.{EncryptionMethod, SignMethod}
import com.karasiq.shadowcloud.model.keys.{KeyChain, KeySet}
import com.karasiq.shadowcloud.ops.region.{BackgroundOps, RegionOps}
import com.karasiq.shadowcloud.ops.storage.StorageOps
import com.karasiq.shadowcloud.ops.supervisor.RegionSupervisorOps
import com.karasiq.shadowcloud.providers.{KeyProvider, SCModules, SessionProvider}
import com.karasiq.shadowcloud.serialization.{SerializationModule, SerializationModules}
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.streams.chunk.ChunkProcessingStreams
import com.karasiq.shadowcloud.streams.file.FileStreams
import com.karasiq.shadowcloud.streams.metadata.MetadataStreams
import com.karasiq.shadowcloud.streams.region.RegionStreams
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
  // Configuration
  // -----------------------------------------------------------------------
  private[this] val rootConfig: Config = _actorSystem.settings.config.getConfig("shadowcloud")
  val config: SCConfig = SCConfig(rootConfig)

  object configs {
    def regionConfig(regionId: RegionId): RegionConfig = {
      RegionConfig.forId(regionId, rootConfig)
    }

    def regionConfig(regionId: RegionId, regionConfig: RegionConfig): RegionConfig = {
      val custom = regionConfig.rootConfig
      val default = this.regionConfig(regionId).rootConfig
      RegionConfig(custom.withFallback(default))
    }

    def storageConfig(storageId: StorageId): StorageConfig = {
      StorageConfig.forId(storageId, rootConfig)
    }

    def storageConfig(storageId: StorageId, storageProps: StorageProps): StorageConfig = {
      StorageConfig.forProps(storageId, storageProps, rootConfig)
    }
  }

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  object implicits {
    implicit val actorSystem: ActorSystem = _actorSystem
    implicit val executionContext: ExecutionContext = _actorSystem.dispatcher
    implicit val materializer: Materializer = ActorMaterializer()(_actorSystem)
    implicit val defaultTimeout: Timeout = Timeout(config.timeouts.query)

    private[ShadowCloudExtension] implicit val provInstantiator: ProviderInstantiator =
      new SCProviderInstantiator(ShadowCloudExtension.this)
  }

  import implicits._

  lazy val modules: SCModules = SCModules(config)
  lazy val serialization: SerializationModule = SerializationModules.forActorSystem(_actorSystem)

  // -----------------------------------------------------------------------
  // User keys and passwords
  // -----------------------------------------------------------------------
  object keys {
    val provider: KeyProvider = provInstantiator.getInstance(config.crypto.keyProvider)

    def generateKeySet(encryption: EncryptionMethod = config.crypto.encryption.keys,
                       signing: SignMethod = config.crypto.signing.index): KeySet = {
      val signingKey = modules.crypto.signModule(signing).createParameters()
      val encryptionKey = modules.crypto.encryptionModule(encryption).createParameters()
      KeySet(UUID.randomUUID(), encryptionKey, signingKey)
    }

    def getOrGenerateChain(): Future[KeyChain] = {
      provider.getKeyChain().flatMap { chain ⇒
        if (chain.encKeys.isEmpty) {
          val keySet = generateKeySet()
          provider
            .addKeySet(keySet)
            .flatMap(_ ⇒ provider.getKeyChain())
            .filter(_.encKeys.contains(keySet))
        } else {
          Future.successful(chain)
        }
      }(executionContexts.cryptography)
    }

    def getGenerationProps(props: SerializedProps): (EncryptionMethod, SignMethod) = {
      val (encMethod, signMethod) = CryptoProps.keyGeneration(props)
      (encMethod.getOrElse(config.crypto.encryption.keys), signMethod.getOrElse(config.crypto.signing.index))
    }
  }

  object passwords {
    val provider: PasswordProvider = provInstantiator.getInstance(config.crypto.passwordProvider)

    def getOrAsk(configPath: String, passwordId: String = null): String = {
      import ConfigImplicits._
      val validId = if (passwordId == null) configPath else passwordId
      rootConfig.withDefault(provider.askPassword(validId), _.getString(configPath))
    }
  }

  object sessions {
    val provider: SessionProvider = provInstantiator.getInstance(config.persistence.sessionProvider)

    def set(storageId: StorageId, key: String, data: AnyRef): Future[Done] = {
      provider.storeSession(storageId, key, serialization.toBytes(data))
    }

    def get[T <: AnyRef : ClassTag](storageId: StorageId, key: String): Future[T] = {
      provider.loadSession(storageId, key).map(serialization.fromBytes[T])
    }
  }

  // -----------------------------------------------------------------------
  // Cache
  // -----------------------------------------------------------------------
  object cache {
    val provider: CacheProvider = provInstantiator.getInstance(config.cache.provider)
    lazy val chunkCache = provider.createChunkCache(_actorSystem)
  }

  // -----------------------------------------------------------------------
  // Actors
  // -----------------------------------------------------------------------
  object actors {
    val regionSupervisor: ActorRef = _actorSystem.actorOf(RegionSupervisor.props, "shadowcloud")
  }

  // -----------------------------------------------------------------------
  // Events
  // -----------------------------------------------------------------------
  object eventStreams { // TODO: Supervisor events
    val region = new StringEventBus[RegionEnvelope](_.regionId)
    val storage = new StringEventBus[StorageEnvelope](_.storageId)

    def publishRegionEvent(regionId: RegionId, event: Any): Unit = {
      region.publish(RegionEnvelope(regionId, event))
    }

    def publishStorageEvent(storageId: StorageId, event: Any): Unit = {
      storage.publish(StorageEnvelope(storageId, event))
    }
  }

  // -----------------------------------------------------------------------
  // Streams
  // -----------------------------------------------------------------------
  object streams {
    lazy val chunk = ChunkProcessingStreams(modules.crypto, config.chunks, config.crypto, config.parallelism)(executionContexts.cryptography)
    lazy val region = RegionStreams(ops.region, config.parallelism, config.timeouts)
    lazy val file = FileStreams(region, chunk)
    lazy val metadata = MetadataStreams(ops.region, this.region, this.file, config.metadata, modules.metadata, config.serialization, serialization)
  }

  object ops {
    lazy val supervisor = RegionSupervisorOps(actors.regionSupervisor, config.timeouts)
    lazy val region = RegionOps(actors.regionSupervisor, config.timeouts, cache.chunkCache)
    lazy val storage = StorageOps(actors.regionSupervisor, config.timeouts)
    lazy val background = BackgroundOps(config, this.region)
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  private[shadowcloud] object executionContexts {
    val metadata = _actorSystem.dispatchers.lookup(SCDispatchers.metadata)
    val metadataBlocking = _actorSystem.dispatchers.lookup(SCDispatchers.metadataBlocking)
    val cryptography = _actorSystem.dispatchers.lookup(SCDispatchers.cryptography)
  }

  def init(): Unit = {
    actors.regionSupervisor // Init actor
  }
}
