package com.karasiq.shadowcloud.actors.utils

import scala.language.postfixOps

import akka.actor.{Actor, ActorRef, Stash, Terminated}

object ContainerActor {
  sealed trait Message
  case object Restart extends Message
}

trait ContainerActor { self: Actor with Stash ⇒
  import ContainerActor._

  var actorRef: Option[ActorRef] = None
  def receiveDefault: Receive
  def startActor(): Unit

  def stopActor(): Unit = {
    actorRef.foreach(context.stop)
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
    unstashAll()
  }

  def afterStop(actor: ActorRef): Unit = {
    actorRef = None
    context.unwatch(actor)
  }

  def receiveContainer: Receive = {
    case Restart ⇒
      stopActor()

    case Terminated(actor) if actorRef.contains(actor) ⇒
      afterStop(actor)
      startActor()

    case message if actorRef.contains(sender()) ⇒
      context.parent ! message

    case message ⇒
      if (actorRef.nonEmpty) {
        actorRef.foreach(_.forward(message))
      } else {
        stash()
      }
  }

  override def receive: Receive = receiveDefault.orElse(receiveContainer)
}
