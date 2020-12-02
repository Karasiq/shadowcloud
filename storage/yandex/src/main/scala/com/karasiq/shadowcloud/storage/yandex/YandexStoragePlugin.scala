package com.karasiq.shadowcloud.storage.yandex

import akka.Done
import akka.actor.{ActorContext, ActorRef}
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
    val sc = ShadowCloud()
    val api = new YandexWebApi(solveCaptcha = { imageUrl ⇒
      sc.challenges
        .create(s"Yandex captcha ($storageId)", s"""<img class="img-responsive" src="$imageUrl"/>""")
        .map(_.utf8String)
    }, passChallenge = { url ⇒
      sc.challenges
        .create(s"Yandex verification ($storageId)", s"""<a href="$url">Please complete the verification</a>""")
        .map(_ ⇒ Done)
    })

    implicit val session: YandexSession = {
      def create() = Await.result(api.createSession(props.credentials.login, props.credentials.password), 5 minutes)

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
