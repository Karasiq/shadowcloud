package com.karasiq.shadowcloud.dropbox

import akka.Done
import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.{ActorMaterializer, Materializer}
import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.dropbox.client.DropboxClient
import com.karasiq.dropbox.model.Dropbox
import com.karasiq.dropbox.model.Dropbox.UserToken
import com.karasiq.dropbox.oauth.DropboxOAuth
import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.actors.SessionProxyActor
import com.karasiq.shadowcloud.actors.utils.SessionAuthenticator
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

import scala.concurrent.Future

object DropboxSessionProxy {
  def props(storageId: StorageId, props: StorageProps)(implicit sc: ShadowCloudExtension): Props = {
    val config = {
      val defaultConfig = sc.config.rootConfig.getConfigIfExists("storage.dropbox")
      sc.configs
        .storageConfig(storageId, props)
        .rootConfig
        .getConfigIfExists("dropbox")
        .withFallback(defaultConfig)
    }

    implicit val requestConfig = {
      val rootConfig = config.getConfigIfExists("requests")
      val appName    = rootConfig.withDefault("shadowcloud/1.0.0", _.getString("app-name"))
      val maxRetries = rootConfig.withDefault(3, _.getInt("max-retries"))
      Dropbox.RequestConfig(appName, maxRetries)
    }

    val appKeys = Dropbox.AppKeys(config.getConfigIfExists("app-keys"))

    SessionProxyActor.props(
      implicit context ⇒
        new SessionAuthenticator[UserToken] {
          private[this] implicit val materializer: Materializer = ActorMaterializer()
          import context.{dispatcher, system}

          def getSession(): Future[UserToken] = {
            def checkConnection(): Future[Done] =
              Http()
                .singleRequest(HttpRequest(uri = "https://dropbox.com/"))
                .filter(_.status.isSuccess())
                .recover { case _ => throw new IllegalStateException("No connection") }
                .map(_ => Done)

            val cachedToken = for {
              _token ← sc.sessions.get[UserToken](storageId, "oauth")
              _      ← { implicit val token = _token; DropboxClient(SCDropbox.DispatcherId).spaceUsage() }
            } yield _token

            cachedToken.recoverWith {
              case _ ⇒
                val oauth = DropboxOAuth()
                for {
                  _ ← checkConnection()
                  _ = sc.ui.showNotification(
                    s"Please authorize shadowcloud in your Dropbox acc: $storageId (${props.credentials.login})\nPress OK to open OAuth web page"
                  )
                  token ← oauth.authenticate(appKeys)
                  Done  ← sc.sessions.set(storageId, "oauth", token)
                } yield token
            }
          }

          def createDispatcher(_token: UserToken): ActorRef = {
            implicit val token = _token
            val client         = DropboxClient(SCDropbox.DispatcherId)
            StoragePluginBuilder(storageId, props)
              .withIndexTree(DropboxRepository(client))
              .withChunksTree(DropboxRepository(client))
              .withHealth(DropboxHealthProvider(client)(context.dispatcher))
              .createStorage()
          }
        }
    )
  }
}
