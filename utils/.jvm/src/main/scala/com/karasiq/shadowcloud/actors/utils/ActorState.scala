package com.karasiq.shadowcloud.actors.utils

import akka.actor.ActorRef

sealed trait ActorState {
  def isActive: Boolean
}

object ActorState {
  final case class Active(dispatcher: ActorRef) extends ActorState {
    override def isActive: Boolean = true
  }
  case object Suspended extends ActorState {
    override def isActive: Boolean = false
  }

  def ifActive(state: ActorState, action: ActorRef ⇒ Unit): Unit = {
    state match {
      case Active(dispatcher) ⇒
        action(dispatcher)

      case _ ⇒
      // Pass
    }
  }
}
