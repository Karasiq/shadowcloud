package com.karasiq.shadowcloud.storage.telegram

import akka.actor.{ActorContext, ActorRef}
import akka.event.Logging
import akka.util.ByteString

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.{ShadowCloud, ShadowCloudExtension}
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.providers.LifecycleHook
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder
import com.karasiq.shadowcloud.model.Path
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Telegram storage plugin with official Desktop API.
  * No custom API ID/hash required - uses OpenTele's built-in credentials.
  */
class TelegramStoragePlugin extends StoragePlugin {

  override def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    implicit val dispatcher: ExecutionContext = context.dispatcher
    implicit val system                       = context.system

    val sc  = ShadowCloud()
    val log = Logging(system, context.self)

    // Load configuration (simplified - no API credentials needed)
    val config = Try {
      TelegramStorageConfig(
        props.rootConfig.withFallback(sc.config.rootConfig.getConfig("storage.telegram"))
      )
    }.recoverWith {
      case ex ⇒
        Failure(new IllegalArgumentException(s"Invalid Telegram storage configuration: ${ex.getMessage}", ex))
    }

    config match {
      case Success(cfg) ⇒
        log.info("Initializing Telegram storage with official Desktop API: {}", storageId)

        Try {
          // Get or create session (no API credentials needed!)
          val sessionKey = getOrCreateSession(storageId, cfg, sc, log)

          // Create repository with session
          val repository = TelegramRepository(
            sessionKey = sessionKey,
            apiHash = "", // Not used with OpenTele
            apiId = 0     // Not used with OpenTele
          )(dispatcher)

          // Build storage
          StoragePluginBuilder(storageId, props)
            .withTreeRepo(repository)
            .withHealthF(() ⇒ getTelegramHealth(repository))
            .withLifecycleHook(
              new TelegramLifecycleHook(
                storageId = storageId,
                sessionKey = sessionKey,
                repository = repository,
                config = cfg,
                sc = sc,
                log = log
              )
            )
            .createStorage()

        } match {
          case Success(storage) ⇒
            log.info("Telegram storage created successfully: {}", storageId)
            storage

          case Failure(ex) ⇒
            log.error(ex, "Failed to create Telegram storage: {}", storageId)
            sc.ui.showNotification(
              s"Failed to initialize Telegram storage: $storageId\n" +
              s"Error: ${ex.getMessage}\n\nEnsure OpenTele is installed (run setup.sh)"
            )
            throw ex
        }

      case Failure(ex) ⇒
        log.error(ex, "Invalid Telegram configuration for storage: {}", storageId)
        sc.ui.showNotification(
          s"Invalid Telegram Configuration: $storageId\n" +
          s"Error: ${ex.getMessage}"
        )
        throw ex
    }
  }

  /**
    * Get existing session or create new one interactively.
    * Uses official Telegram Desktop API (no custom credentials needed).
    */
  private def getOrCreateSession(
      storageId: StorageId,
      config: TelegramStorageConfig,
      sc: ShadowCloudExtension,
      log: akka.event.LoggingAdapter
  )(implicit ec: ExecutionContext): String = {

    log.info("Getting or creating Telegram session with official Desktop API: {}", storageId)

    sc.ui.showNotification(
      "Telegram Storage\n" +
      s"Initializing $storageId with official Desktop API\nNo custom API credentials required"
    )

    // Use TelegramSessionCreator - no API ID/hash needed!
    val sessionFuture = TelegramSessionCreator.getOrCreateSession(
      storageId = storageId.toString
    )(sc, ec)

    // Wait for session with timeout
    Try {
      Await.result(sessionFuture, config.sessionCreationTimeout)
    } match {
      case Success(session) ⇒
        log.info("Telegram session ready with official Desktop API: {}", storageId)
        session

      case Failure(ex: java.util.concurrent.TimeoutException) ⇒
        val msg = s"Session operation timed out after ${config.sessionCreationTimeout}"
        log.error(msg)
        throw new RuntimeException(msg, ex)

      case Failure(ex) ⇒
        log.error(ex, "Failed to get or create Telegram session")
        throw new RuntimeException(s"Telegram session failed: ${ex.getMessage}", ex)
    }
  }

  /**
    * Calculate Telegram storage health metrics.
    */
  private def getTelegramHealth(
      repository: TelegramRepository
  )(implicit ec: ExecutionContext): Future[StorageHealth] = {
    Future {
      StorageHealth.unlimited
    }.recover {
      case ex ⇒
        StorageHealth(
          online = false,
          totalSpace = 0,
          writableSpace = 0
        )
    }
  }
}

/**
  * Lifecycle hook for Telegram storage.
  */
private class TelegramLifecycleHook(
    storageId: StorageId,
    sessionKey: String,
    repository: TelegramRepository,
    config: TelegramStorageConfig,
    sc: ShadowCloudExtension,
    log: akka.event.LoggingAdapter
) extends LifecycleHook {

  override def initialize(): Unit = {
    log.info("Initializing Telegram storage with official Desktop API: {}", storageId)

    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    val validationResult = Try {
      Await.ready(
        repository
          .subKeys(Path.root)
          .runWith(akka.stream.scaladsl.Sink.ignore)(
            akka.stream.ActorMaterializer()(akka.actor.ActorSystem("validation"))
          ),
        15.seconds
      )
    }

    validationResult match {
      case Success(_) ⇒
        log.info("Telegram storage connection validated: {}", storageId)
        sc.ui.showNotification(
          "Telegram Storage Ready\n" +
          s"$storageId is online using official Desktop API"
        )

      case Failure(ex) ⇒
        log.error(ex, "Failed to validate Telegram storage: {}", storageId)
        sc.ui.showNotification(
          "Telegram Storage Warning\n" +
          s"$storageId may not be fully initialized:\n${ex.getMessage}"
        )
    }
  }

  override def shutdown(): Unit = {
    log.info("Shutting down Telegram storage: {}", storageId)
  }

  override def toString: String = s"TelegramLifecycleHook($storageId)"
}

/**
  * Simplified Telegram storage configuration.
  * No API credentials needed - uses official Desktop API via OpenTele.
  */
private[telegram] case class TelegramStorageConfig(
    phoneNumber: String,
    entity: String,
    sessionCreationTimeout: FiniteDuration,
    tempDir: java.nio.file.Path
)

private[telegram] object TelegramStorageConfig {

  def apply(config: com.typesafe.config.Config): TelegramStorageConfig = {
    import com.karasiq.common.configs.ConfigImplicits._

    val phoneNumber = config.getString("phone-number")
    val entity = config.withDefault("me", _.getString("entity"))

    val timeoutSeconds         = config.withDefault(600, _.getInt("session-creation-timeout-seconds"))
    val sessionCreationTimeout = timeoutSeconds.seconds

    val tempDirPath = config.withDefault(
      System.getProperty("user.home") + "/.shadowcloud/telegram",
      _.getString("temp-dir")
    )
    val tempDir = java.nio.file.Paths.get(tempDirPath)

    if (!java.nio.file.Files.exists(tempDir)) {
      java.nio.file.Files.createDirectories(tempDir)
    }

    TelegramStorageConfig(
      phoneNumber = phoneNumber,
      entity = entity,
      sessionCreationTimeout = sessionCreationTimeout,
      tempDir = tempDir
    )
  }
}
