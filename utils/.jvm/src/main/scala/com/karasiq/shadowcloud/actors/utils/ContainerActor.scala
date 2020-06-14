package com.karasiq.shadowcloud.actors.utils

import java.util.concurrent.TimeoutException

import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, ReceiveTimeout, Stash, SupervisorStrategy, Terminated}

import scala.concurrent.duration._
import scala.util.control.NonFatal

private[actors] object ContainerActor {
  sealed trait Message
  case object Restart extends Message
}

private[actors] trait ContainerActor { self: Actor with Stash with ActorLogging ⇒
  import ContainerActor._

  protected var actorRef: Option[ActorRef] = None
  protected var stopping: Boolean          = false

  def startActor(): Unit

  def restartInterval: FiniteDuration = 5 seconds

  def stopActor(): Unit = {
    stopping = true
    actorRef.foreach { ref ⇒
      context.watch(ref)
      context.stop(ref)
    }
    context.setReceiveTimeout(15 seconds)
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
    stopping = false
  }

  override def unhandled(message: Any): Unit = message match {
    case Restart ⇒
      stopActor()

    case Terminated(actor) if actorRef.contains(actor) ⇒
      if (stopping) {
        afterStop(actor)
        startActor()
      } else {
        log.warning("Contained actor stopped unexpectedly: {}", actor)

        import context.dispatcher
        context.system.scheduler.scheduleOnce(restartInterval, context.self, Restart)
      }

    case ReceiveTimeout ⇒
      log.error("Actor restart timeout: {}", actorRef.mkString)
      context.stop(context.self)

    case message if actorRef.contains(sender()) ⇒
      context.parent ! message

    case message ⇒
      if (actorRef.nonEmpty) {
        actorRef.foreach(_.forward(message))
      } else {
        stash()
      }
  }

  override def supervisorStrategy: _root_.akka.actor.SupervisorStrategy = OneForOneStrategy() {
    case NonFatal(_) ⇒ SupervisorStrategy.Stop
  }
}
