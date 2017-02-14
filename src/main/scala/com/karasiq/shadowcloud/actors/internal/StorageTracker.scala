package com.karasiq.shadowcloud.actors.internal

import akka.actor.{ActorContext, ActorRef}
import com.karasiq.shadowcloud.actors.internal.ChunksTracker.ChunkStatus

import scala.collection.mutable
import scala.language.postfixOps
import scala.util.Random

private[actors] final class StorageTracker(implicit context: ActorContext) {
  private[this] val dispatchers = mutable.AnyRefMap[ActorRef, String]() // TODO: Quota

  def contains(dispatcher: ActorRef): Boolean = {
    dispatchers.contains(dispatcher)
  }

  def getForRead(status: ChunkStatus): Seq[ActorRef] = {
    Random.shuffle(dispatchers.keySet.intersect(status.dispatchers).toVector)
  }

  def getForWrite(chunk: ChunkStatus): Seq[ActorRef] = {
    Random.shuffle(dispatchers.keys.toVector)
  }

  def getIdOf(dispatcher: ActorRef): String = {
    dispatchers(dispatcher)
  }

  def register(indexId: String, dispatcher: ActorRef): Unit = {
    context.watch(dispatcher)
    dispatchers += dispatcher → indexId
  }

  def unregister(dispatcher: ActorRef): Unit = {
    context.unwatch(dispatcher)
    dispatchers -= dispatcher
  }
}
