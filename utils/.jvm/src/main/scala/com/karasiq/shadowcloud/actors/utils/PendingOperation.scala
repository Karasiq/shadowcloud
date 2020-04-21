package com.karasiq.shadowcloud.actors.utils

import akka.actor.ActorRef

import scala.collection.mutable


object PendingOperation {
  def apply[Key <: AnyRef]: PendingOperation[Key] = {
    new PendingOperation[Key]()
  }
}

class PendingOperation[Key <: AnyRef] {
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

  def removeWaiter(actor: ActorRef): Unit = {
    subscribers
      .withFilter(_._2.contains(actor))
      .foreach {
        case (key, actors) ⇒
          actors -= actor
          if (actors.isEmpty) subscribers -= key
      }
  }

  def count: Int = {
    subscribers.size
  }

  def finish(key: Key, result: AnyRef)(implicit sender: ActorRef = ActorRef.noSender): Unit = {
    subscribers.remove(key).foreach(_.foreach(_ ! result))
  }

  def finishAll(f: Key => AnyRef)(implicit sender: ActorRef = ActorRef.noSender): Unit = {
    subscribers.foreach {
      case (key, actors) =>
        val result = f(key)
        actors.foreach(_ ! result)
    }
    subscribers.clear()
  }
}
