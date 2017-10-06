package com.karasiq.shadowcloud.mailrucloud

import java.util.concurrent.TimeoutException

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{Actor, Props, ReceiveTimeout, Stash, Status}
import akka.pattern.pipe

import com.karasiq.mailrucloud.api.MailCloudClient
import com.karasiq.mailrucloud.api.MailCloudTypes.{CsrfToken, Nodes, Session}
import com.karasiq.shadowcloud.mailrucloud.MailRuCloudProxyActor.MCSession
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

private[mailrucloud] object MailRuCloudProxyActor {
  final case class MCSession(session: Session, token: CsrfToken, nodes: Nodes)

  def props(storageId: StorageId, props: StorageProps): Props = {
    Props(new MailRuCloudProxyActor(storageId, props))
  }
}

private[mailrucloud] class MailRuCloudProxyActor(storageId: StorageId, props: StorageProps) extends Actor with Stash {
  import context.dispatcher
  val client = MailCloudClient()(context.system)
  val timeoutSchedule = context.system.scheduler.scheduleOnce(1 minute, self, ReceiveTimeout)

  def receive = {
    case MCSession(session, token, nodes) ⇒
      timeoutSchedule.cancel()
      becomeInitialized(session, token, nodes)

    case Status.Failure(error) ⇒
      throw error

    case ReceiveTimeout ⇒
      throw new TimeoutException("Auth timeout")

    case _ ⇒
      stash()
  }

  override def preStart(): Unit = {
    super.preStart()
    startAuth()
  }

  def startAuth(): Unit = {
    val future = for {
      session ← client.login(props.credentials.login, props.credentials.password)
      token ← client.csrfToken(session)
      nodes ← client.nodes(session, token)
    } yield MCSession(session, token, nodes)

    future.pipeTo(self)
  }

  def becomeInitialized(implicit session: Session, token: CsrfToken, nodes: Nodes): Unit = {
    val storageDispatcher = StoragePluginBuilder(storageId, props)
      .withChunksTree(MailRuCloudRepository(client))
      .withIndexTree(MailRuCloudRepository(client))
      .withHealth(MailRuCloudHealthProvider(client))
      .createStorage()

    context.become {
      case message if sender() == storageDispatcher ⇒
        context.parent ! message

      case message ⇒
        storageDispatcher.forward(message)
    }

    unstashAll()
  }
}
