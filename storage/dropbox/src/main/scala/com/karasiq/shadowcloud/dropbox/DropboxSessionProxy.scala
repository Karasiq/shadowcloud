package com.karasiq.shadowcloud.dropbox

import akka.Done
import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.dropbox.client.DropboxClient
import com.karasiq.dropbox.model.Dropbox
import com.karasiq.dropbox.model.Dropbox.UserToken
import com.karasiq.dropbox.oauth.DropboxOAuth
import com.karasiq.shadowcloud.{ShadowCloud, ShadowCloudExtension}
import com.karasiq.shadowcloud.actors.SessionProxyActor
import com.karasiq.shadowcloud.actors.utils.SessionAuthenticator
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

object DropboxSessionProxy {
  def props(storageId: StorageId, props: StorageProps)(implicit sc: ShadowCloudExtension): Props = {
    val config = sc.configs.storageConfig(storageId, props)
    implicit val requestConfig = {
      val rootConfig = config.rootConfig.getConfigIfExists("dropbox.requests")
      val appName = rootConfig.withDefault("shadowcloud/1.0.0", _.getString("app-name"))
      val maxRetries = rootConfig.withDefault(5, _.getInt("max-retries"))
      Dropbox.RequestConfig(appName, maxRetries)
    }

    SessionProxyActor.props(implicit context ⇒ new SessionAuthenticator[UserToken] {
      import context.dispatcher
      implicit val materializer = ActorMaterializer()
      implicit val actorSystem = ActorSystem()
      val sc = ShadowCloud(context.system)
      val config = sc.configs.storageConfig(storageId, props)

      implicit val requestConfig = {
        val rootConfig = config.rootConfig.getConfigIfExists("dropbox.requests")
        val appName = rootConfig.withDefault("shadowcloud/1.0.0", _.getString("app-name"))
        val maxRetries = rootConfig.withDefault(5, _.getInt("max-retries"))
        Dropbox.RequestConfig(appName, maxRetries)
      }

      val appKeys = Dropbox.AppKeys(config.rootConfig.getConfigIfExists("dropbox.app-keys"))

      def getSession() = {
        val cachedToken = for {
          _token ← sc.sessions.get[UserToken](storageId, "oauth")
          _ ← { implicit val token = _token; DropboxClient().spaceUsage() }
        } yield _token

        cachedToken.recoverWith { case _ ⇒
          val oauth = DropboxOAuth()
          for {
            token ← oauth.authenticate(appKeys)
            Done ← sc.sessions.set(storageId, "oauth", token)
          } yield token
        }
      }

      def createDispatcher(_token: UserToken) = {
        implicit val token = _token
        val client = DropboxClient()
        StoragePluginBuilder(storageId, props)
          .withIndexTree(DropboxRepository(client))
          .withChunksTree(DropboxRepository(client))
          .withHealth(DropboxHealthProvider(client)(context.dispatcher))
          .createStorage()
      }
    })
  }
}
