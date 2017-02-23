package com.karasiq.shadowcloud.actors.internal

import akka.actor.ActorRef
import com.karasiq.shadowcloud.index.Chunk

import scala.collection.mutable
import scala.language.postfixOps

private[actors] final class PendingOperation[Key <: AnyRef] {
  private[this] val subscribers = mutable.AnyRefMap[Key, mutable.Set[ActorRef]]()

  def addWaiter(key: Key, actor: ActorRef, ifFirst: () ⇒ Unit = () ⇒ ()): Unit = {
    subscribers.get(key) match {
      case Some(actors) ⇒
        actors.add(actor)

      case None ⇒
        subscribers += key → mutable.Set(actor)
        ifFirst()
    }
  }

  def finish(key: Key, result: AnyRef)(implicit sender: ActorRef = ActorRef.noSender): Unit = {
    subscribers.remove(key).foreach(_.foreach(_ ! result))
  }
}

private[actors] object PendingOperation {
  def withChunk: PendingOperation[Chunk] = new PendingOperation
}