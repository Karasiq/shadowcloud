package com.karasiq.shadowcloud.actors.internal

import akka.actor.{ActorContext, ActorRef}
import com.karasiq.shadowcloud.actors.events.StorageEvent
import com.karasiq.shadowcloud.actors.internal.ChunksTracker.ChunkStatus
import com.karasiq.shadowcloud.index.diffs.IndexDiff

import scala.collection.mutable
import scala.language.postfixOps
import scala.util.Random

private[actors] final class StorageTracker(implicit context: ActorContext) { // TODO: Quota
  private[this] val storageIds = mutable.AnyRefMap[ActorRef, String]()
  private[this] val dispatchers = mutable.AnyRefMap[String, ActorRef]()

  def contains(dispatcher: ActorRef): Boolean = {
    storageIds.contains(dispatcher)
  }

  def contains(storageId: String): Boolean = {
    dispatchers.contains(storageId)
  }

  def all: Iterable[ActorRef] = {
    storageIds.keys
  }

  def forIndexWrite(diff: IndexDiff): Seq[ActorRef] = { // TODO: Selective write
    all.toSeq
  }

  def forRead(status: ChunkStatus): Seq[ActorRef] = {
    Random.shuffle(storageIds.keySet.intersect(status.dispatchers).toVector)
  }

  def forWrite(chunk: ChunkStatus): Seq[ActorRef] = {
    Random.shuffle(storageIds.keys.toVector)
  }

  def getStorageId(dispatcher: ActorRef): String = {
    storageIds(dispatcher)
  }

  def getDispatcher(storageId: String): ActorRef = {
    dispatchers(storageId)
  }

  def register(storageId: String, dispatcher: ActorRef): Unit = {
    context.watch(dispatcher)
    storageIds += dispatcher → storageId
    dispatchers += storageId → dispatcher
    StorageEvent.stream.subscribe(context.self, storageId)
  }

  def unregister(dispatcher: ActorRef): Unit = {
    context.unwatch(dispatcher)
    storageIds.remove(dispatcher).foreach { storageId ⇒
      dispatchers -= storageId
      StorageEvent.stream.unsubscribe(context.self, storageId)
    }
  }
}
