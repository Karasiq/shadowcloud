package com.karasiq.shadowcloud.actors.internal

import akka.actor.{ActorContext, ActorRef}
import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.crypto.EncryptionModule
import com.karasiq.shadowcloud.index.{Chunk, ChunkIndexDiff}
import com.karasiq.shadowcloud.utils.Utils

import scala.collection.mutable
import scala.language.postfixOps

private[actors] object ChunksTracker {
  object Status extends Enumeration {
    val PENDING, STORED = Value
  }
  case class ChunkStatus(status: Status.Value, time: Long, chunk: Chunk, dispatchers: Set[ActorRef] = Set.empty, waiters: Set[ActorRef] = Set.empty)
}

private[actors] final class ChunksTracker(storages: StorageTracker, log: LoggingAdapter)(implicit context: ActorContext) {
  import ChunksTracker._
  private[this] implicit val sender: ActorRef = context.self
  private[this] val chunks = mutable.AnyRefMap[ByteString, ChunkStatus]()

  def readChunk(chunk: Chunk, receiver: ActorRef): Option[ChunkStatus] = {
    val status = chunks.get(chunk.checksum.hash)
    status match {
      case Some(status) ⇒
        if (!Utils.isSameChunk(status.chunk, chunk)) {
          receiver ! ReadChunk.Failure(chunk, new IllegalArgumentException(s"Chunks conflict: ${status.chunk} / $chunk"))
        } else if (status.chunk.data.encrypted.nonEmpty) {
          log.info("Chunk extracted from cache: {}", status.chunk)
          receiver ! ReadChunk.Success(status.chunk, Source.single(status.chunk.data.encrypted))
        } else {
          storages.getForRead(status).headOption match {
            case Some(dispatcher) ⇒
              log.info("Reading chunk from {}: {}", dispatcher, chunk)
              dispatcher.tell(ReadChunk(chunk), receiver)

            case None ⇒
              receiver ! ReadChunk.Failure(chunk, new IllegalArgumentException("Chunk unavailable"))
          }
        }

      case None ⇒
        receiver ! ReadChunk.Failure(chunk, new IllegalArgumentException("Chunk not found"))
    }
    status
  }

  private def storageWriteChunk(status: ChunkStatus): Set[ActorRef] = {
    require(status.chunk.data.nonEmpty)
    val selected = storages.getForWrite(status) // TODO: Replication
    selected.foreach(_ ! WriteChunk(status.chunk))
    selected.toSet
  }

  def writeChunk(chunk: Chunk, receiver: ActorRef): ChunkStatus = {
    require(chunk.data.nonEmpty && chunk.checksum.hash.nonEmpty)
    chunks.get(chunk.checksum.hash) match {
      case Some(stored) if stored.status == Status.STORED ⇒
        val chunkWithData = if (Utils.isSameChunk(stored.chunk, chunk) && chunk.data.encrypted.nonEmpty) {
          chunk
        } else {
          val encryptor = EncryptionModule(stored.chunk.encryption.method)
          stored.chunk.copy(data = chunk.data.copy(encrypted = encryptor.encrypt(chunk.data.plain, stored.chunk.encryption)))
        }
        receiver ! WriteChunk.Success(chunkWithData, chunkWithData)
        log.info("Chunk restored from index, write skipped: {}", chunkWithData)
        stored.copy(chunk = chunkWithData)

      case Some(pending) if pending.status == Status.PENDING ⇒
        context.watch(receiver)
        log.info("Already writing chunk, added to queue: {}", chunk)
        val newStatus = pending.copy(waiters = pending.waiters + receiver)
        chunks += chunk.checksum.hash → newStatus
        newStatus

      case None ⇒
        context.watch(receiver)
        val status = ChunkStatus(Status.PENDING, Utils.timestamp, chunk, Set.empty, Set(receiver))
        val written = storageWriteChunk(status)
        log.info("Writing chunk to {}: {}", written, chunk)
        val newStatus = status.copy(dispatchers = written)
        chunks += chunk.checksum.hash → newStatus
        newStatus
    }
  }

  def retryPendingChunks(): Unit = {
    chunks.foreach { case (hash, status) ⇒
      if (status.status == Status.PENDING && status.dispatchers.isEmpty) {
        val written = storageWriteChunk(status)
        if (written.nonEmpty) {
          log.debug("Retrying chunk write to {}: {}", written, status)
          chunks += hash → status.copy(dispatchers = written)
        }
      }
    }
  }

  def registerChunk(dispatcher: ActorRef, chunk: Chunk): Unit = {
    val hash: ByteString = chunk.checksum.hash
    chunks.get(hash) match {
      case Some(status) if !Utils.isSameChunk(status.chunk, chunk) ⇒
        log.error("Chunk conflict: {} / {}", status.chunk, chunk)

      case Some(status)  ⇒
        if (status.status == Status.PENDING) {
          log.debug("Resolved pending chunk: {}", chunk)
          require(status.chunk.data.nonEmpty)
          status.waiters.foreach(_ ! WriteChunk.Success(status.chunk, status.chunk))
          chunks += chunk.checksum.hash → status.copy(status = Status.STORED, chunk = status.chunk.withoutData, dispatchers = Set(dispatcher), waiters = Set.empty)
        } else {
          log.debug("Chunk duplicate found on {}: {}", dispatcher, chunk)
          chunks += hash → status.copy(dispatchers = status.dispatchers + dispatcher)
        }

      case None ⇒
        chunks += hash → ChunkStatus(Status.STORED, Utils.timestamp, chunk.withoutData, Set(dispatcher))
    }
  }

  private def removeActor(status: ChunkStatus, actor: ActorRef): Unit = {
    if (!status.dispatchers.contains(actor) && !status.waiters.contains(actor)) return
    val hash = status.chunk.checksum.hash
    val newStatus = status.copy(dispatchers = status.dispatchers - actor, waiters = status.waiters - actor)
    if (status.status == Status.STORED && newStatus.dispatchers.isEmpty) {
      log.warning("Chunk is lost: {}", newStatus.chunk)
      chunks -= hash
    } else if (status.status == Status.PENDING && newStatus.waiters.isEmpty) {
      log.warning("Chunk write cancelled: {}", newStatus.chunk)
      chunks -= hash
    } else {
      chunks += hash → newStatus
    }
  }

  def unregister(dispatcher: ActorRef): Unit = {
    chunks.foreachValue(removeActor(_, dispatcher))
  }

  def unregisterChunk(dispatcher: ActorRef, chunk: Chunk): Unit = {
    chunks.get(chunk.checksum.hash) match {
      case Some(status) ⇒
        if (Utils.isSameChunk(status.chunk, chunk)) {
          removeActor(status, dispatcher)
        } else {
          log.warning("Unknown chunk deleted: {} (existing: {})", chunk, status.chunk)
        }

      case None ⇒
        log.debug("Chunk not found: {}", chunk)
    }
  }

  def update(dispatcher: ActorRef, diff: ChunkIndexDiff): Unit = {
    diff.deletedChunks.foreach(unregisterChunk(dispatcher, _))
    diff.newChunks.foreach(registerChunk(dispatcher, _))
  }
}
