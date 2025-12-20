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

import akka.stream.ActorMaterializer

/**
  * Telegram storage plugin with official Desktop API.
  * No custom API ID/hash required - uses OpenTele's built-in credentials.
  */
class TelegramStoragePlugin extends StoragePlugin {

  override def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    implicit val dispatcher: ExecutionContext = context.dispatcher
    implicit val system                       = context.system
    implicit val sc                           = ShadowCloud()
    import sc.implicits.materializer
    val log = Logging(system, context.self)

    // Load configuration
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
          // Get or create session
          val sessionKey = getOrCreateSession(storageId, cfg, log)

          // Initialize daemon
          val daemon = new TelegramDaemon(
            sessionString = sessionKey,
            entity = cfg.entity
          )

          // Wait for daemon to initialize
          Await.result(daemon.start(), cfg.daemonInitTimeout)

          log.info("Telegram daemon initialized for entity: {}", cfg.entity)

          // Create repository wrapper
          val repository = TelegramRepository(
            daemon = daemon,
            sc = sc,
            log = log
          )

          // Build storage
          StoragePluginBuilder(storageId, props)
            .withTreeRepo(repository)
            .withHealthF(() ⇒ getTelegramHealth(daemon))
            .withLifecycleHook(
              new TelegramLifecycleHook(
                storageId = storageId,
                daemon = daemon,
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
                s"Error: ${ex.getMessage}\n\nEnsure resources are extracted (run setup scripts)"
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
    * Uses official Telegram Desktop API.
    */
  private def getOrCreateSession(
      storageId: StorageId,
      config: TelegramStorageConfig,
      log: akka.event.LoggingAdapter
  )(implicit ec: ExecutionContext, sc: ShadowCloudExtension): String = {
    log.info("Getting or creating Telegram session with official Desktop API: {}", storageId)

    sc.ui.showNotification(
      "Telegram Storage\n" +
        s"Initializing $storageId with official Desktop API\nNo custom API credentials required"
    )

    // Use TelegramSessionCreator
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
      daemon: TelegramDaemon
  )(implicit ec: ExecutionContext): Future[StorageHealth] = {
    daemon.isClientConnected().map { connected ⇒
      StorageHealth.unlimited.copy(online = connected)
    }
  }
}

/**
  * Lifecycle hook for Telegram storage.
  */
private class TelegramLifecycleHook(
    storageId: StorageId,
    daemon: TelegramDaemon,
    config: TelegramStorageConfig,
    sc: ShadowCloudExtension,
    log: akka.event.LoggingAdapter
) extends LifecycleHook {

  override def initialize(): Unit = {
    log.info("Initializing Telegram storage: {}", storageId)

    val validationResult = Try {
      Await.ready(
        daemon.list(limit = 1),
        15.seconds
      )
    }

    validationResult match {
      case Success(_) ⇒
        log.info("Telegram storage connection validated: {}", storageId)
        sc.ui.showNotification(
          "Telegram Storage Ready\n" +
            s"$storageId is online"
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

    Try {
      Await.result(daemon.shutdown(), 10.seconds)
    }.recover {
      case ex ⇒
        log.warning(s"Error during daemon shutdown: ${ex.getMessage}")
    }
  }

  override def toString: String = s"TelegramLifecycleHook($storageId)"
}
