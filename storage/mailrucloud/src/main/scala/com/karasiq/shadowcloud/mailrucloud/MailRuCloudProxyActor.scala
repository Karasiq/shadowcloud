package com.karasiq.shadowcloud.mailrucloud

import akka.actor.Props
import com.karasiq.mailrucloud.api.MailCloudClient
import com.karasiq.mailrucloud.api.MailCloudTypes.{CsrfToken, Nodes, Session}
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.SessionProxyActor
import com.karasiq.shadowcloud.actors.utils.SessionAuthenticator
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

import scala.concurrent.Future

private[mailrucloud] object MailRuCloudProxyActor {
  final case class MailCloudSession(session: Session, token: CsrfToken, nodes: Nodes)

  def props(storageId: StorageId, props: StorageProps): Props = {
    SessionProxyActor.props(implicit context ⇒
      new SessionAuthenticator[MailCloudSession] {
        import context.dispatcher
        implicit val sc = ShadowCloud()
        val client      = MailCloudClient()(context.system)

        override def getSession(): Future[MailCloudSession] = {
          def getSessionCached() = {
            for {
              session ← sc.sessions.get[Session](storageId, "mailcloud-session")
              token   ← client.csrfToken(session)
              nodes   ← client.nodes(session, token)
            } yield MailCloudSession(session, token, nodes)
          }

          def getSession() = {
            for {
              session ← client.login(props.credentials.login, props.credentials.password)
              _       ← sc.sessions.set(storageId, "mailcloud-session", session)
              token   ← client.csrfToken(session)
              nodes   ← client.nodes(session, token)
            } yield MailCloudSession(session, token, nodes)
          }

          getSessionCached().recoverWith { case _ ⇒ getSession() }
        }

        def createDispatcher(_session: MailCloudSession) = {
          implicit val MailCloudSession(session, token, nodes) = _session
          StoragePluginBuilder(storageId, props)
            .withChunksTree(MailRuCloudRepository(client))
            .withIndexTree(MailRuCloudRepository(client))
            .withHealth(MailRuCloudHealthProvider(client))
            .createStorage()
        }
      }
    )
  }
}
