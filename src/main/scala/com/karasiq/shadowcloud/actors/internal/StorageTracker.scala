package com.karasiq.shadowcloud.actors.internal

import akka.actor.{ActorContext, ActorRef}
import com.karasiq.shadowcloud.actors.events.StorageEvent
import com.karasiq.shadowcloud.actors.internal.ChunksTracker.ChunkStatus
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.StorageHealth

import scala.collection.mutable
import scala.language.postfixOps

private[actors] object StorageTracker {
  case class Storage(id: String, dispatcher: ActorRef, health: StorageHealth)
}

private[actors] final class StorageTracker(implicit context: ActorContext) { // TODO: Quota
  import StorageTracker.Storage
  private[this] val storages = mutable.AnyRefMap[String, Storage]()
  private[this] val storagesByAR = mutable.AnyRefMap[ActorRef, Storage]()

  def contains(dispatcher: ActorRef): Boolean = {
    storagesByAR.contains(dispatcher)
  }

  def contains(storageId: String): Boolean = {
    storages.contains(storageId)
  }

  def available(toWrite: Long = 0): Seq[ActorRef] = {
    storagesByAR.values.toSeq
      .filter(_.health.canWrite > toWrite)
      .sortBy(_.id)
      .map(_.dispatcher)
  }

  def forIndexWrite(diff: IndexDiff): Seq[ActorRef] = {
    available(1024) // At least 1KB
  }

  def forRead(status: ChunkStatus): Seq[ActorRef] = {
    available().filter(status.hasChunk.contains)
  }

  def forWrite(chunk: ChunkStatus): Seq[ActorRef] = {
    def dispatcherCanWrite(dispatcher: ActorRef): Boolean = {
      !chunk.hasChunk.contains(dispatcher) &&
        !chunk.writingChunk.contains(dispatcher) &&
        !chunk.waitingChunk.contains(dispatcher)
    }
    val writeSize = chunk.chunk.checksum.encryptedSize
    available(writeSize).filter(dispatcherCanWrite)
  }

  def getStorageId(dispatcher: ActorRef): String = {
    storagesByAR(dispatcher).id
  }

  def getDispatcher(storageId: String): ActorRef = {
    storages(storageId).dispatcher
  }

  def register(storageId: String, dispatcher: ActorRef, health: StorageHealth): Unit = {
    context.watch(dispatcher)
    val storage = Storage(storageId, dispatcher, health)
    storages += storageId → storage
    storagesByAR += dispatcher → storage
    StorageEvent.stream.subscribe(context.self, storageId)
  }

  def unregister(dispatcher: ActorRef): Unit = {
    context.unwatch(dispatcher)
    storagesByAR.remove(dispatcher).foreach { storage ⇒
      storages -= storage.id
      StorageEvent.stream.unsubscribe(context.self, storage.id)
    }
  }

  def update(storageId: String, health: StorageHealth): Unit = {
    storages.get(storageId).foreach { storage ⇒
      val newStatus = storage.copy(health = health)
      storages += storageId → newStatus
      storagesByAR += storage.dispatcher → newStatus
    }
  }
}
