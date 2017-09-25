package com.karasiq.shadowcloud.storage.gdrive

import akka.actor.{Actor, ActorContext, ActorRef, Props}

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.gdrive.context.GDriveContext
import com.karasiq.gdrive.files.GDriveService
import com.karasiq.gdrive.oauth.GDriveOAuth
import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder
import com.karasiq.shadowcloud.utils.Utils

private[gdrive] object GDriveStoragePlugin {
  def apply(implicit sc: ShadowCloudExtension): GDriveStoragePlugin = {
    new GDriveStoragePlugin()
  }
}

private[gdrive] class GDriveStoragePlugin(implicit sc: ShadowCloudExtension) extends StoragePlugin {
  private[this] def defaultConfig = sc.config.rootConfig.getConfigIfExists("storage.gdrive")

  def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext) = {
    val proxyProps = Props(new Actor {
      import context.{dispatcher ⇒ executionContext} // API dispatcher

      def receiveAuthorized(storageDispatcher: ActorRef): Receive = {
        case message if sender() == storageDispatcher ⇒
          context.parent ! message

        case message ⇒ 
          storageDispatcher.forward(message)
      }

      def receive = {
        case message ⇒
          implicit val driveContext = {
            val dataStore = SCGDriveStore(storageId, props.credentials.login)
            val config = props.rootConfig.getConfigIfExists("gdrive").withFallback(defaultConfig)
            GDriveContext(config, dataStore)
          }

          val oauth = GDriveOAuth()
          implicit val session = oauth.authorize(props.credentials.login)

          val applicationName = props.rootConfig.withDefault("shadowcloud", _.getString("gdrive.application-name"))
          val service = GDriveService(applicationName)

          val dispatcher = StoragePluginBuilder(storageId, props)
            .withIndexTree(GDriveRepository(service))
            .withChunksTree(GDriveRepository(service))
            .withHealth(GDriveHealthProvider(service))
            .createStorage()

          context.become(receiveAuthorized(dispatcher))
          self.forward(message)
      }
    })

    context.actorOf(proxyProps.withDispatcher(GDriveDispatchers.apiDispatcherId), Utils.uniqueActorName("gdrive-proxy"))
  }
}
