package com.karasiq.shadowcloud.actors.internal

import scala.collection.mutable
import scala.language.{implicitConversions, postfixOps}

import akka.actor.{ActorContext, ActorRef}
import akka.event.LoggingAdapter
import akka.util.ByteString

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ChunkPath, ReadChunk => SReadChunk, WriteChunk => SWriteChunk}
import com.karasiq.shadowcloud.actors.RegionDispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.utils.PendingOperation
import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.index.diffs.ChunkIndexDiff
import com.karasiq.shadowcloud.utils.Utils

private[actors] object ChunksTracker {
  object Status extends Enumeration {
    val PENDING, STORED = Value
  }

  case class ChunkStatus(status: Status.Value, time: Long, chunk: Chunk, writingChunk: Set[ActorRef] = Set.empty,
                         hasChunk: Set[ActorRef] = Set.empty, waitingChunk: Set[ActorRef] = Set.empty)

  def apply(regionId: String, config: RegionConfig, storages: StorageTracker,
            log: LoggingAdapter)(implicit context: ActorContext): ChunksTracker = {
    new ChunksTracker(regionId, config, storages, log)
  }
}

private[actors] final class ChunksTracker(regionId: String, config: RegionConfig, storages: StorageTracker,
                                          log: LoggingAdapter)(implicit context: ActorContext) {
  import ChunksTracker._

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] implicit val sender: ActorRef = context.self
  private[this] val sc = ShadowCloud()
  private[this] val chunks = mutable.AnyRefMap[ByteString, ChunkStatus]()
  private[this] val readingChunks = PendingOperation.withChunk

  // -----------------------------------------------------------------------
  // Read/write
  // -----------------------------------------------------------------------
  def readChunk(chunk: Chunk, receiver: ActorRef): Option[ChunkStatus] = {
    val statusOption = getChunk(chunk)
    statusOption match {
      case Some(status) ⇒
        if (!Utils.isSameChunk(status.chunk, chunk)) {
          receiver ! ReadChunk.Failure(chunk, new IllegalArgumentException(s"Chunks conflict: ${status.chunk} / $chunk"))
        } else if (status.chunk.data.encrypted.nonEmpty) {
          log.debug("Chunk extracted from cache: {}", status.chunk)
          receiver ! ReadChunk.Success(chunk, status.chunk)
        } else {
          readingChunks.addWaiter(chunk, receiver, () ⇒ readChunkFromStorage(status))
        }

      case None ⇒
        receiver ! ReadChunk.Failure(chunk, new IllegalArgumentException("Chunk not found"))
    }
    statusOption
  }

  def writeChunk(chunk: Chunk, receiver: ActorRef): ChunkStatus = {
    require(chunk.nonEmpty)
    getChunk(chunk) match {
      case Some(stored) if stored.status == Status.STORED ⇒
        val chunkWithData = if (Utils.isSameChunk(stored.chunk, chunk) && chunk.data.encrypted.nonEmpty) {
          chunk
        } else {
          val encryptor = sc.modules.encryptionModule(stored.chunk.encryption.method)
          stored.chunk.copy(data = chunk.data.copy(encrypted = encryptor.encrypt(chunk.data.plain, stored.chunk.encryption)))
        }
        receiver ! WriteChunk.Success(chunkWithData, chunkWithData)
        log.debug("Chunk restored from index, write skipped: {}", chunkWithData)
        stored.copy(chunk = chunkWithData)

      case Some(pending) if pending.status == Status.PENDING ⇒
        context.watch(receiver)
        log.debug("Already writing chunk, added to queue: {}", chunk)
        putStatus(pending.copy(waitingChunk = pending.waitingChunk + receiver))

      case None ⇒
        context.watch(receiver)
        val status = ChunkStatus(Status.PENDING, Utils.timestamp, chunk, waitingChunk = Set(receiver))
        val written = writeChunkToStorages(status)
        if (written.isEmpty) {
          log.warning("No storages available for write: {}", chunk)
        } else {
          log.debug("Writing chunk to {}: {}", written, chunk)
        }
        putStatus(status.copy(writingChunk = written.map(_.dispatcher)))
    }
  }

  def retryPendingChunks(): Unit = {
    chunks.foreachValue { status ⇒
      if (status.status == Status.PENDING && status.hasChunk.isEmpty) {
        val written = writeChunkToStorages(status)
        if (written.nonEmpty) {
          log.debug("Retrying chunk write to {}: {}", written, status)
          putStatus(status.copy(hasChunk = written.map(_.dispatcher)))
        }
      }
    }
  }

  // -----------------------------------------------------------------------
  // Update state
  // -----------------------------------------------------------------------
  def registerChunk(dispatcher: ActorRef, chunk: Chunk): ChunkStatus = {
    getChunk(chunk) match {
      case Some(status) if !Utils.isSameChunk(status.chunk, chunk) ⇒
        log.error("Chunk conflict: {} / {}", status.chunk, chunk)
        status

      case Some(status)  ⇒
        if (status.status == Status.PENDING) { // Chunk is pending
          val newStatus = status.copy(writingChunk = status.writingChunk - dispatcher,
            hasChunk = status.hasChunk + dispatcher)
          val needWrites = math.max(0, config.dataReplicationFactor - newStatus.hasChunk.size)
          if (needWrites == 0) {
            require(status.chunk.data.nonEmpty)
            log.debug("Resolved pending chunk: {}", chunk)
            status.waitingChunk.foreach(_ ! WriteChunk.Success(status.chunk, status.chunk))
            putStatus(newStatus.copy(status = Status.STORED,
              chunk = status.chunk.withoutData, waitingChunk = Set.empty))
          } else {
            log.debug("Need {} more writes for {}", needWrites, chunk)
            putStatus(newStatus)
          }
        } else if (!status.hasChunk.contains(dispatcher)) {
          log.debug("Chunk duplicate found on {}: {}", dispatcher, chunk)
          putStatus(status.copy(writingChunk = status.writingChunk - dispatcher, hasChunk = status.hasChunk + dispatcher))
        } else {
          status
        }

      case None ⇒ // Chunk first seen
        putStatus(ChunkStatus(Status.STORED, Utils.timestamp, chunk.withoutData, hasChunk = Set(dispatcher)))
    }
  }

  def unregister(dispatcher: ActorRef): Unit = {
    chunks.foreachValue(removeActorRef(_, dispatcher))
    readingChunks.removeWaiter(dispatcher)
  }

  def unregisterChunk(dispatcher: ActorRef, chunk: Chunk): Unit = {
    getChunk(chunk) match {
      case Some(status) ⇒
        if (Utils.isSameChunk(status.chunk, chunk)) {
          removeActorRef(status, dispatcher)
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

  // -----------------------------------------------------------------------
  // Callbacks
  // -----------------------------------------------------------------------
  def readSuccess(chunk: Chunk): Unit = {
    require(chunk.nonEmpty, "Chunk is empty")
    readingChunks.finish(chunk, ReadChunk.Success(chunk, chunk))
  }

  def readFailure(chunk: Chunk, error: Throwable): Unit = {
    readingChunks.finish(chunk, ReadChunk.Failure(chunk, error))
  }

  // -----------------------------------------------------------------------
  // Internal functions
  // -----------------------------------------------------------------------
  private[this] def getChunkPath(storage: StorageTracker.Storage, chunk: Chunk): ChunkPath = {
    ChunkPath(regionId, storage.config.chunkKey(chunk))
  }

  private[this] def getChunk(chunk: Chunk): Option[ChunkStatus] = {
    chunks.get(chunk.checksum.hash)
  }

  private[this] def putStatus(status: ChunkStatus): ChunkStatus = {
    chunks += status.chunk.checksum.hash → status
    status
  }

  private[this] def removeStatus(status: ChunkStatus): Option[ChunkStatus] = {
    chunks.remove(status.chunk.checksum.hash)
  }

  private[this] def writeChunkToStorages(status: ChunkStatus): Set[StorageTracker.Storage] = {
    require(status.chunk.data.nonEmpty, "Chunks is empty")
    val writingCount = status.writingChunk.size + status.hasChunk.size
    val availableStorages = storages.forWrite(status)
    val selectedStorages = if (writingCount > 0) {
      availableStorages.take(config.dataReplicationFactor - writingCount)
    } else {
      Utils.takeOrAll(availableStorages, config.dataReplicationFactor)
    }
    selectedStorages.foreach(storage ⇒ storage.dispatcher ! SWriteChunk(getChunkPath(storage, status.chunk), status.chunk))
    selectedStorages.toSet
  }

  private[this] def readChunkFromStorage(status: ChunkStatus) = {
    val chunk = status.chunk.withoutData
    storages.forRead(status).headOption match {
      case Some(storage) ⇒
        log.debug("Reading chunk from {}: {}", storage.id, chunk)
        storage.dispatcher ! SReadChunk(getChunkPath(storage, status.chunk), chunk)

      case None ⇒
        readingChunks.finish(status.chunk, ReadChunk.Failure(chunk, new IllegalArgumentException("Chunk unavailable")))
    }
  }

  private[this] def removeActorRef(status: ChunkStatus, actor: ActorRef): Unit = {
    if (!status.hasChunk.contains(actor) && !status.writingChunk.contains(actor) &&
      !status.waitingChunk.contains(actor)) return

    val newStatus = status.copy(writingChunk = status.writingChunk - actor,
      hasChunk = status.hasChunk - actor, waitingChunk = status.waitingChunk - actor)
    if (status.status == Status.STORED && newStatus.hasChunk.isEmpty) {
      log.warning("Chunk is lost: {}", newStatus.chunk)
      removeStatus(status)
    } else if (status.status == Status.PENDING && newStatus.waitingChunk.isEmpty) {
      log.warning("Chunk write cancelled: {}", newStatus.chunk)
      removeStatus(status)
    } else {
      putStatus(newStatus)
    }
  }
}
