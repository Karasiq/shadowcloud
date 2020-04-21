package com.karasiq.shadowcloud.actors

import java.util.concurrent.TimeoutException

import akka.actor.{Actor, ActorContext, Props, ReceiveTimeout, Stash, Status}
import akka.pattern.pipe
import com.karasiq.shadowcloud.actors.utils.SessionAuthenticator

import scala.concurrent.duration._

private[shadowcloud] object SessionProxyActor {
  def props[Session](createAuthenticator: ActorContext ⇒ SessionAuthenticator[Session]): Props = {
    Props(new SessionProxyActor[Session](createAuthenticator))
  }
}

private[shadowcloud] class SessionProxyActor[Session](createAuthenticator: ActorContext ⇒ SessionAuthenticator[Session]) extends Actor with Stash {
  import context.dispatcher
  val state = createAuthenticator(context)
  val timeoutSchedule = context.system.scheduler.scheduleOnce(5 minutes, self, ReceiveTimeout)

  def receive = {
    case Status.Success(session: Session @unchecked) ⇒
      timeoutSchedule.cancel()
      becomeInitialized(session)

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
    state.getSession().map(Status.Success(_)).pipeTo(self)
  }

  def becomeInitialized(session: Session): Unit = {
    val dispatcher = state.createDispatcher(session)

    context.become {
      case message if sender() == dispatcher ⇒
        context.parent ! message

      case message ⇒
        dispatcher.forward(message)
    }

    unstashAll()
  }
}
