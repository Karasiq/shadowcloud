package com.karasiq.shadowcloud.actors.utils

import akka.actor.ActorRef

sealed trait ActorState

object ActorState {
  final case class Active(dispatcher: ActorRef) extends ActorState
  case object Suspended extends ActorState

  def ifActive(state: ActorState, action: ActorRef ⇒ Unit): Unit = {
    state match {
      case Active(dispatcher) ⇒
        action(dispatcher)

      case _ ⇒
      // Pass
    }
  }
}
