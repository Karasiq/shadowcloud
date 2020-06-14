package com.karasiq.shadowcloud.storage.yandex

import java.awt.Desktop
import java.net.URI

import akka.Done
import akka.actor.{ActorContext, ActorRef}
import akka.event.Logging
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder
import com.karasiq.shadowcloud.storage.yandex.YandexWebApi.{YandexSession, YandexUsedSpace}
import com.karasiq.shadowcloud.storage.{StorageHealthProvider, StoragePlugin}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

class YandexStoragePlugin extends StoragePlugin {
  override def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    import context.{dispatcher, system}
    val log = Logging(system, context.self)
    val sc  = ShadowCloud()
    val api = new YandexWebApi(solveCaptcha = { imageUrl ⇒
      sc.ui.showNotification(s"Yandex captcha required for $storageId: $imageUrl")
      if (Desktop.isDesktopSupported) Desktop.getDesktop.browse(new URI(imageUrl))
      Future.fromTry(Try(sc.ui.askPassword("Yandex captcha")))
    }, passChallenge = { url ⇒
      if (Desktop.isDesktopSupported) Desktop.getDesktop.browse(new URI(url))
      Future {
        sc.ui.showNotification(
          s"Yandex verification required for $storageId: $url\n" +
            "Please complete the verification and then press OK"
        )
        Done
      }
    })

    implicit val session: YandexSession = {
      def create() = Await.result(api.createSession(props.credentials.login, props.credentials.password), 10 seconds)

      Try(sc.sessions.getBlocking[YandexSession](storageId, "yad-session"))
        .map { implicit session ⇒
          Await.result(api.usedSpace(), 10 seconds)
          session
        }
        .getOrElse {
          val newSession = create()
          sc.sessions.setBlocking(storageId, "yad-session", newSession)
          newSession
        }
    }

    val repository = new YandexRepository(api)

    StoragePluginBuilder(storageId, props)
      .withChunksTree(repository)
      .withIndexTree(repository)
      .withHealth(new StorageHealthProvider {
        override def health: Future[StorageHealth] =
          api.usedSpace().map {
            case YandexUsedSpace(used, free, limit) ⇒
              StorageHealth.normalized(free, limit, used)
          }
      })
      .createStorage()
  }
}
