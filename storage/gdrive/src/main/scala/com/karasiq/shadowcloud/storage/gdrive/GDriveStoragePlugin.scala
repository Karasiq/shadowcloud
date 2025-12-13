package com.karasiq.shadowcloud.storage.gdrive

import akka.actor.{Actor, ActorContext, ActorRef, Props}
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.Drive
import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

private[gdrive] object GDriveStoragePlugin {
  def apply(implicit sc: ShadowCloudExtension): GDriveStoragePlugin = {
    new GDriveStoragePlugin()
  }
}

/**
 * Google Drive Storage Plugin using official Google APIs v3.
 *
 * Supports:
 * - OAuth 2.0 authentication with credential caching
 * - Automatic folder hierarchy creation
 * - Stream-based file operations (read/write/delete)
 * - Health monitoring (used space calculation)
 */
private[gdrive] class GDriveStoragePlugin(implicit sc: ShadowCloudExtension) extends StoragePlugin {
  private[this] def defaultConfig = sc.config.rootConfig.getConfigIfExists("storage.gdrive")

  def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext) = {
    import context.{dispatcher => executionContext}

    val config = sc.configs
      .storageConfig(storageId, props)
      .rootConfig
      .getConfigIfExists("gdrive")
      .withFallback(defaultConfig)

    val proxyProps = Props(new Actor {
      implicit val ec: ExecutionContext = executionContext

      def receiveAuthorized(storageDispatcher: ActorRef): Receive = {
        case message if sender() == storageDispatcher ⇒
          context.parent ! message

        case message ⇒
          storageDispatcher.forward(message)
      }

      def receive = {
        case message ⇒
          Try {
            // Initialize Google Drive service
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance

            // Get OAuth credentials from configuration or cache
            val credentials = getOrRefreshCredentials(
              storageId,
              props,
              config,
              httpTransport,
              jsonFactory
            )

            // Build the Drive service
            val driveService = new Drive.Builder(
              httpTransport,
              jsonFactory,
              credentials
            )
              .setApplicationName(config.withDefault("shadowcloud", _.getString("application-name")))
              .build()

            // Create repository and storage dispatcher
            val repository = GDriveRepository(driveService)(ec)
            val dispatcher = StoragePluginBuilder(storageId, props)
              .withTreeRepo(repository)
              .withHealthF(() ⇒ calculateUsedSpace(driveService))
              .createStorage()

            context.become(receiveAuthorized(dispatcher))
            self.forward(message)

          } match {
            case Success(_) ⇒ // Already handled
            case Failure(ex) ⇒
              sc.ui.showNotification(
                s"Failed to initialize Google Drive storage: $storageId\n${ex.getMessage}"
              )
              context.stop(self)
          }
      }
    })

    context.actorOf(proxyProps.withDispatcher(GDriveDispatchers.apiDispatcherId), "gdrive-proxy")
  }

  /**
   * Get or refresh OAuth credentials using official Google Auth library.
   */
  private def getOrRefreshCredentials(
    storageId: StorageId,
    props: StorageProps,
    config: com.typesafe.config.Config,
    httpTransport: com.google.api.client.http.HttpTransport,
    jsonFactory: com.google.api.client.json.JsonFactory
  )(implicit ec: ExecutionContext): com.google.auth.oauth2.UserCredentials = {
    // OAuth 2.0 flow configuration
    val scopes = java.util.Arrays.asList(DriveScopes.DRIVE)
    val clientId = config.withDefault("YOUR_CLIENT_ID", _.getString("client-id"))
    val clientSecret = config.withDefault("YOUR_CLIENT_SECRET", _.getString("client-secret"))

    // Try to load cached token from session
    val cachedToken = Try {
      sc.sessions.getRawBlocking(storageId, "gdrive-refresh-token")
        .map(bs ⇒ new String(bs.toArray))
    }.toOption.flatten.getOrElse {
      // If no cached token, would need to initiate OAuth flow here
      // For now, throw to indicate need for authorization
      throw new IllegalStateException(
        s"No cached credentials for $storageId. Please authorize via web UI."
      )
    }

    // Build credentials from refresh token
    com.google.auth.oauth2.UserCredentials.newBuilder()
      .setClientId(clientId)
      .setClientSecret(clientSecret)
      .setRefreshToken(cachedToken)
      .build()
  }

  /**
   * Calculate used space in Google Drive account.
   */
  private def calculateUsedSpace(
    driveService: Drive
  )(implicit ec: ExecutionContext): scala.concurrent.Future[com.karasiq.shadowcloud.model.utils.StorageHealth] = {
    scala.concurrent.Future {
      val about = driveService.about().get()
        .setFields("storageQuota")
        .execute()

      val usedSpace = Option(about.getStorageQuota)
        .flatMap(sq ⇒ Option(sq.getUsageInDrive))
        .getOrElse(0L)

      val totalSpace = Option(about.getStorageQuota)
        .flatMap(sq ⇒ Option(sq.getLimit))
        .getOrElse(Long.MaxValue)

      com.karasiq.shadowcloud.model.utils.StorageHealth(
        usedSpace = usedSpace,
        totalSpace = totalSpace
      )
    }
  }
}
