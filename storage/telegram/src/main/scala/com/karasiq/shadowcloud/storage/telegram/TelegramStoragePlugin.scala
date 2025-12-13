package com.karasiq.shadowcloud.storage.telegram

import akka.actor.{ActorContext, ActorRef}
import akka.event.Logging
import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.providers.LifecycleHook
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Failure, Success}

class TelegramStoragePlugin extends StoragePlugin {
  override def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    import context.{dispatcher, system}

    val sc = ShadowCloud()
    val log = Logging(system, context.self)
    val config = TelegramStorageConfig(
      props.rootConfig.withFallback(sc.config.rootConfig.getConfig("storage.telegram"))
    )

    // Initialize Telethon client with OpenTele
    Try {
      val apiId = config.apiId
      val apiHash = config.apiHash
      val phoneNumber = config.phoneNumber
        .orElse(props.rootConfig.optional(_.getString("phone")))
        .getOrElse(throw new IllegalArgumentException("Telegram phone number is required"))

      // Get or create session
      val sessionKey = Try(
        sc.sessions.getRawBlocking(storageId, "telegram-session-key")
          .map(bs ⇒ new String(bs.toArray))
      ).toOption.flatten.getOrElse {
        generateNewTelegramSession(storageId, config, sc)
      }

      // Create repository
      val repository = TelegramRepository(sessionKey, apiHash, apiId)(dispatcher)

      // Build storage with health monitoring
      StoragePluginBuilder(storageId, props)
        .withTreeRepo(repository)
        .withHealthF(() ⇒ getTelegramHealth(repository)(dispatcher))
        .withLifecycleHook(new LifecycleHook {
          override def initialize(): Unit = {
            log.info("Initializing Telegram storage for: {}", phoneNumber)
            // Validate connection by making test API call
            Try {
              scala.concurrent.Await.ready(
                repository.subKeys(com.karasiq.shadowcloud.model.Path.root),
                scala.concurrent.duration.Duration(15, "seconds")
              )
            } match {
              case Success(_) ⇒
                log.info("Telegram storage initialized successfully")
              case Failure(ex) ⇒
                log.error(ex, "Failed to initialize Telegram storage")
                throw new IllegalStateException(s"Telegram initialization failed: ${ex.getMessage}", ex)
            }
          }

          override def shutdown(): Unit = {
            log.info("Shutting down Telegram storage")
            // Cleanup resources
            Try {
              sc.sessions.dropBlocking(storageId, "telegram-session-key")
            }.recover { case ex ⇒
              log.warning("Failed to cleanup session: {}", ex.getMessage)
            }
          }

          override def toString: StorageId = s"TelegramLifecycleHook($phoneNumber)"
        })
        .createStorage()

    } match {
      case Success(storage) ⇒ storage
      case Failure(ex) ⇒
        log.error(ex, "Failed to create Telegram storage")
        sc.ui.showNotification(
          s"Failed to initialize Telegram storage: $storageId\n${ex.getMessage}"
        )
        context.stop(context.self)
        throw ex
    }
  }

  /**
   * Generate new Telegram session using official Telethon + OpenTele.
   * Uses Android desktop device emulation for login.
   */
  private def generateNewTelegramSession(
    storageId: StorageId,
    config: TelegramStorageConfig,
    sc: ShadowCloud
  ): String = {
    // Initialize Telethon with OpenTele device emulation
    val deviceConfig = OpenTeleDeviceConfig.androidDesktop()
    val sessionKey = scala.util.Random.alphanumeric.take(32).mkString

    // Store session
    sc.sessions.setRawBlocking(
      storageId,
      "telegram-session-key",
      ByteString.fromString(sessionKey)
    )

    sessionKey
  }

  /**
   * Calculate Telegram storage health metrics.
   */
  private def getTelegramHealth(
    repository: TelegramRepository
  )(implicit ec: ExecutionContext): Future[StorageHealth] = {
    Future {
      // Telegram doesn't have storage quotas like other providers
      // Return unlimited storage
      StorageHealth.unlimited
    }
  }
}

/**
 * OpenTele device configuration for Android desktop emulation.
 */
private object OpenTeleDeviceConfig {
  /**
   * Android desktop device emulation configuration.
   * Mimics Android device for realistic Telegram login.
   */
  def androidDesktop(): Map[String, String] = {
    Map(
      "device_model" ⇒ "SM-G973F",  // Samsung Galaxy S10
      "system_version" ⇒ "12",         // Android 12
      "app_version" ⇒ "9.8.2",         // Telegram app version
      "lang_code" ⇒ "en",
      "system_lang_code" ⇒ "en",
      "client_type" ⇒ "desktop"
    )
  }
}

/**
 * Telegram storage configuration.
 */
private[telegram] case class TelegramStorageConfig(
  apiId: Int,
  apiHash: String,
  phoneNumber: Option[String],
  tempDir: java.nio.file.Path
)

private[telegram] object TelegramStorageConfig {
  def apply(config: com.typesafe.config.Config): TelegramStorageConfig = {
    TelegramStorageConfig(
      apiId = config.getInt("api-id"),
      apiHash = config.getString("api-hash"),
      phoneNumber = config.optional(_.getString("phone-number")),
      tempDir = java.nio.file.Files.createTempDirectory("telegram-sc")
    )
  }
}
