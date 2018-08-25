package com.karasiq.shadowcloud.actors

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Kill, PossiblyHarmful, Props, Terminated}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import com.karasiq.shadowcloud.model.utils.StorageHealth

object StorageHealthSupervisor {
  // Messages
  sealed trait Message

  // Internal messages
  private sealed trait InternalMessage extends Message with PossiblyHarmful
  private case object Check extends InternalMessage
  private case class Success(health: StorageHealth) extends InternalMessage
  private case object Failure extends InternalMessage

  // Events
  sealed trait Event

  // Props
  def props(actor: ActorRef, interval: FiniteDuration, maxFailures: Int): Props = {
    Props(classOf[StorageHealthSupervisor], actor, interval, maxFailures)
  }
}

class StorageHealthSupervisor(actor: ActorRef, interval: FiniteDuration, maxFailures: Int) extends Actor with ActorLogging {
  import context.dispatcher

  import StorageHealthSupervisor._
  implicit val timeout = Timeout(interval)

  var schedule: Cancellable = _
  var failures = 0

  override def receive: Receive = {
    case Check ⇒
      (actor ? StorageDispatcher.GetHealth)
        .mapTo[StorageDispatcher.GetHealth.Success]
        .filter(_.result.online)
        .map(hs ⇒ Success(hs.result))
        .recover { case _ ⇒ Failure }
        .pipeTo(self)

    case Failure ⇒
      failures += 1
      log.debug("Health check failure #{}", failures)
      if (failures >= maxFailures) {
        log.warning("Health checks failed ({}), restarting storage: {}", failures, actor)
        actor ! Kill
      }

    case Success(health) ⇒
      log.debug("Health check passed: {}", health)
      failures = 0

    case Terminated(`actor`) ⇒
      context.stop(self)

    case message if sender() == actor ⇒
      context.parent.forward(message)

    case message ⇒
      actor.forward(message)
  }

  override def preStart(): Unit = {
    super.preStart()
    context.watch(actor)
    schedule = context.system.scheduler.schedule(interval, interval, self, Check)
  }

  override def postStop(): Unit = {
    Option(schedule).foreach(_.cancel())
    super.postStop()
  }
}
