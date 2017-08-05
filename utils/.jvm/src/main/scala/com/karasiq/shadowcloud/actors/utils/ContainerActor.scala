package com.karasiq.shadowcloud.actors.utils

import java.util.concurrent.TimeoutException

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{Actor, ActorRef, ReceiveTimeout, Stash, Terminated}

private[actors] object ContainerActor {
  sealed trait Message
  case object Restart extends Message
}

private[actors] trait ContainerActor { self: Actor with Stash ⇒
  import ContainerActor._

  protected var actorRef: Option[ActorRef] = None

  def startActor(): Unit

  def stopActor(): Unit = {
    actorRef.foreach(context.stop)
    context.setReceiveTimeout(30 seconds)
  }

  def restartActor(): Unit = {
    if (actorRef.isEmpty) {
      startActor()
    } else {
      stopActor()
    }
  }

  def afterStart(actor: ActorRef): Unit = {
    actorRef = Some(actor)
    context.watch(actor)
    context.setReceiveTimeout(Duration.Inf)
    unstashAll()
  }

  def afterStop(actor: ActorRef): Unit = {
    actorRef = None
    context.unwatch(actor)
  }

  override def unhandled(message: Any): Unit = message match {
    case Restart ⇒
      stopActor()

    case Terminated(actor) if actorRef.contains(actor) ⇒
      afterStop(actor)
      startActor()

    case ReceiveTimeout ⇒
      throw new TimeoutException("Actor restart timeout")

    case message if actorRef.contains(sender()) ⇒
      context.parent ! message

    case message ⇒
      if (actorRef.nonEmpty) {
        actorRef.foreach(_.forward(message))
      } else {
        stash()
      }
  }
}
