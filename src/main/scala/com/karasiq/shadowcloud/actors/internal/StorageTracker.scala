package com.karasiq.shadowcloud.actors.internal

import akka.actor.{Actor, ActorRef}
import com.karasiq.shadowcloud.actors.internal.ChunksTracker.ChunkStatus

import scala.collection.mutable
import scala.language.postfixOps
import scala.util.Random

private[actors] class StorageTracker(self: Actor) {
  import self.context
  private[this] val dispatchers = mutable.Set[ActorRef]() // TODO: Quota

  def contains(dispatcher: ActorRef): Boolean = {
    dispatchers.contains(dispatcher)
  }

  def getForRead(status: ChunkStatus): Seq[ActorRef] = {
    Random.shuffle(dispatchers.intersect(status.dispatchers).toVector)
  }

  def getForWrite(chunk: ChunkStatus): Seq[ActorRef] = {
    Random.shuffle(dispatchers.toVector)
  }

  def register(dispatcher: ActorRef): Unit = {
    context.watch(dispatcher)
    dispatchers += dispatcher
  }

  def unregister(dispatcher: ActorRef): Unit = {
    context.unwatch(dispatcher)
    dispatchers -= dispatcher
  }
}
