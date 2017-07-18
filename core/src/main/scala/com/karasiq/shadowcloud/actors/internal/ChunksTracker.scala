package com.karasiq.shadowcloud.actors.internal

import scala.collection.mutable
import scala.language.{implicitConversions, postfixOps}

import akka.actor.{ActorContext, ActorRef}
import akka.event.LoggingAdapter
import akka.util.ByteString

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ChunkPath, ReadChunk ⇒ SReadChunk, WriteChunk ⇒ SWriteChunk}
import com.karasiq.shadowcloud.actors.RegionDispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.utils.PendingOperation
import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.index.diffs.ChunkIndexDiff
import com.karasiq.shadowcloud.storage.replication.{ChunkAvailability, ChunkStatusProvider, ChunkWriteAffinity, StorageSelector}
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.{ChunkStatus, WriteStatus}
import com.karasiq.shadowcloud.storage.replication.StorageStatusProvider.StorageStatus
import com.karasiq.shadowcloud.utils.Utils

private[actors] object ChunksTracker {
  def apply(regionId: String, config: RegionConfig, storages: StorageTracker,
            log: LoggingAdapter)(implicit context: ActorContext): ChunksTracker = {
    new ChunksTracker(regionId, config, storages, log)
  }
}

// TODO: Refactor
private[actors] final class ChunksTracker(regionId: String, config: RegionConfig, storages: StorageTracker,
                                          log: LoggingAdapter)(implicit context: ActorContext) extends ChunkStatusProvider {

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] implicit val sender: ActorRef = context.self
  private[this] val sc = ShadowCloud()
  private[this] val chunksMap = mutable.AnyRefMap[ByteString, ChunkStatus]()
  private[this] val readingChunks = PendingOperation.withChunk

  // -----------------------------------------------------------------------
  // Read/write
  // -----------------------------------------------------------------------
  def readChunk(chunk: Chunk, receiver: ActorRef)(implicit storageSelector: StorageSelector): Option[ChunkStatus] = {
    val statusOption = getChunkStatus(chunk)
    statusOption match {
      case Some(status) ⇒
        if (!Utils.isSameChunk(status.chunk, chunk)) {
          receiver ! ReadChunk.Failure(chunk, new IllegalArgumentException(s"Chunks conflict: ${status.chunk} / $chunk"))
        } else if (status.chunk.data.nonEmpty) {
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

  def writeChunk(chunk: Chunk, receiver: ActorRef)(implicit storageSelector: StorageSelector): ChunkStatus = {
    require(chunk.nonEmpty)
    getChunkStatus(chunk) match {
      case Some(status) ⇒
        status.writeStatus match {
          case WriteStatus.Pending(_) ⇒
            context.watch(receiver)
            log.debug("Already writing chunk, added to queue: {}", chunk)
            putStatus(status.copy(waitingChunk = status.waitingChunk + receiver))

          case WriteStatus.Finished ⇒
            val chunkWithData = if (Utils.isSameChunk(status.chunk, chunk) && chunk.data.encrypted.nonEmpty) {
              chunk
            } else {
              // TODO: Move decryption to stream
              val encryptor = sc.modules.crypto.encryptionModule(status.chunk.encryption.method)
              status.chunk.copy(data = chunk.data.copy(encrypted = encryptor.encrypt(chunk.data.plain, status.chunk.encryption)))
            }
            receiver ! WriteChunk.Success(chunkWithData, chunkWithData)
            log.debug("Chunk restored from index, write skipped: {}", chunkWithData)
            status.copy(chunk = chunkWithData)
        }

      case None ⇒
        context.watch(receiver)
        val status = ChunkStatus(WriteStatus.Pending(ChunkWriteAffinity.empty), Utils.timestamp, chunk, waitingChunk = Set(receiver))
        val affinity = storageSelector.forWrite(status)
        val statusWithAffinity = status.copy(WriteStatus.Pending(affinity))
        val written = writeChunkToStorages(statusWithAffinity)
        if (written.isEmpty) {
          log.warning("No storages available for write: {}", chunk)
        } else {
          log.debug("Writing chunk to {}: {}", written, chunk)
        }
        putStatus(statusWithAffinity.copy(availability = ChunkAvailability(writingChunk = written.map(_.id).toSet)))
    }
  }

  def repairChunk(chunk: Chunk, newAffinity: Option[ChunkWriteAffinity], receiver: ActorRef)(implicit storageSelector: StorageSelector): Unit = {
    getChunkStatus(chunk) match {
      case Some(status) ⇒
        status.writeStatus match {
          case WriteStatus.Pending(oldAffinity) ⇒
            val affinity = newAffinity.getOrElse(oldAffinity)
            val newStatus = status.copy(
              WriteStatus.Pending(affinity),
              waitingChunk = status.waitingChunk + receiver
            )
            putStatus(tryFinishChunk(newStatus, affinity))

          case WriteStatus.Finished ⇒
            if (!Utils.isSameChunk(status.chunk, chunk)) {
              receiver ! WriteChunk.Failure(chunk, new IllegalArgumentException(s"Chunk conflict: ${status.chunk} / $chunk"))
            } else if (chunk.isEmpty) {
              receiver ! WriteChunk.Failure(chunk, new IllegalArgumentException("Chunk should not be empty"))
            } else {
              val status1 = status.copy(
                WriteStatus.Pending(ChunkWriteAffinity.empty),
                chunk = chunk,
                waitingChunk = status.waitingChunk + receiver
              )
              val affinity = newAffinity.getOrElse(storageSelector.forWrite(status1))
              val status2 = status1.copy(WriteStatus.Pending(affinity))
              log.debug("Repairing chunk: {}", status2)
              putStatus(tryFinishChunk(status2, affinity))
            }
        }
    }
    retryPendingChunks()
  }

  def retryPendingChunks()(implicit storageSelector: StorageSelector): Unit = {
    chunksMap.foreachValue {
      case cs @ ChunkStatus(WriteStatus.Pending(affinity), _, _, _, _) ⇒
        if (affinity.isFinished(cs)) {
          log.debug("Marking chunk as finished: {}", cs)
          putStatus(cs.finished)
        } else {
          val written = writeChunkToStorages(cs)
          if (written.nonEmpty) {
            log.debug("Retrying chunk write to {}: {}", written, cs)
            val writtenIds = written.map(_.id)
            putStatus(cs.copy(availability = cs.availability.copy(writingChunk =
              cs.availability.writingChunk ++ writtenIds)))
          }
        }

      case _ ⇒
        // Skip 
    }
  }

  // -----------------------------------------------------------------------
  // Update state
  // -----------------------------------------------------------------------
  private[this] def tryFinishChunk(status: ChunkStatus, affinity: ChunkWriteAffinity): ChunkStatus = {
    if (affinity.isWrittenEnough(status)) {
      require(status.chunk.data.nonEmpty)
      log.debug("Resolved pending chunk: {}", status.chunk)
      status.waitingChunk.foreach(_ ! WriteChunk.Success(status.chunk, status.chunk))
      val resultStatus = if (affinity.isFinished(status)) {
        status.finished
      } else {
        status
      }
      resultStatus.copy(waitingChunk = Set.empty)
    } else {
      log.debug("Need more writes for {}", status.chunk)
      status
    }
  }

  def registerChunk(dispatcher: ActorRef, chunk: Chunk)(implicit storageSelector: StorageSelector): ChunkStatus = {
    val storageId = storages.getStorageId(dispatcher)
    getChunkStatus(chunk) match {
      case Some(status) if !Utils.isSameChunk(status.chunk, chunk) ⇒
        log.error("Chunk conflict: {} / {}", status.chunk, chunk)
        status

      case Some(status)  ⇒
        status.writeStatus match {
          case WriteStatus.Pending(affinity) ⇒
            val status1 = status.copy(availability = status.availability.finished(storageId))
            putStatus(tryFinishChunk(status1, affinity))

          case WriteStatus.Finished ⇒
            if (!status.availability.isWritten(storageId)) {
              log.debug("Chunk duplicate found on {}: {}", dispatcher, chunk)
              putStatus(status.copy(availability = status.availability.finished(storageId)))
            } else {
              status
            }
        }

      case None ⇒
        putStatus(ChunkStatus(WriteStatus.Finished, Utils.timestamp,
          chunk.withoutData, ChunkAvailability.empty.finished(storageId)))
    }
  }

  def unregister(dispatcher: ActorRef): Unit = {
    chunksMap.foreachValue(removeActorRef(_, dispatcher))
    readingChunks.removeWaiter(dispatcher)
  }

  def unregisterChunk(dispatcher: ActorRef, chunk: Chunk): Unit = {
    getChunkStatus(chunk) match {
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

  def update(dispatcher: ActorRef, diff: ChunkIndexDiff)(implicit storageSelector: StorageSelector): Unit = {
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
  override def getChunkStatus(chunk: Chunk): Option[ChunkStatus] = {
    chunksMap.get(chunk.checksum.hash)
  }

  override def chunksStatus: Iterable[ChunkStatus] = {
    chunksMap.values
  }

  private[this] def getChunkPath(storage: StorageStatus, chunk: Chunk): ChunkPath = {
    ChunkPath(regionId, storage.config.chunkKey(chunk))
  }

  private[this] def putStatus(status: ChunkStatus): ChunkStatus = {
    chunksMap += status.chunk.checksum.hash → status
    status
  }

  private[this] def removeStatus(status: ChunkStatus): Option[ChunkStatus] = {
    chunksMap.remove(status.chunk.checksum.hash)
  }

  private[this] def writeChunkToStorages(status: ChunkStatus)(implicit storageSelector: StorageSelector): Seq[StorageStatus] = {
    require(status.chunk.data.nonEmpty, "Chunks is empty")
    val storageIds = status.writeStatus match {
      case WriteStatus.Pending(affinity) ⇒
        affinity.selectForWrite(status)

      case WriteStatus.Finished ⇒
        Vector.empty
    }
    val selectedStorages = storageIds.map(storages.getStorage)
    selectedStorages.foreach(storage ⇒ storage.dispatcher ! SWriteChunk(getChunkPath(storage, status.chunk), status.chunk))
    selectedStorages
  }

  private[this] def readChunkFromStorage(status: ChunkStatus)(implicit storageSelector: StorageSelector): Option[StorageStatus] = {
    val chunk = status.chunk.withoutData
    val storage = storageSelector.forRead(status)
    storage match {
      case Some(storage) ⇒
        log.debug("Reading chunk from {}: {}", storage.id, chunk)
        storage.dispatcher ! SReadChunk(getChunkPath(storage, status.chunk), chunk)

      case None ⇒
        readingChunks.finish(status.chunk, ReadChunk.Failure(chunk, new IllegalArgumentException("Chunk unavailable")))
    }
    storage
  }

  private[this] def removeActorRef(status: ChunkStatus, actor: ActorRef): Unit = {
    def removeStorage(status: ChunkStatus, actor: ActorRef): Unit = {
      val storageId = storages.getStorageId(actor)
      if (status.availability.contains(storageId) || status.waitingChunk.contains(actor)) {
        val newStatus = status.copy(
          availability = status.availability - storageId,
          waitingChunk = status.waitingChunk - actor
        )

        status.writeStatus match {
          case WriteStatus.Pending(_) ⇒
            putStatus(newStatus)

          case WriteStatus.Finished ⇒
            if (newStatus.availability.isEmpty) {
              log.warning("Chunk is lost: {}", newStatus.chunk)
              removeStatus(status)
            }
        }
      }
    }

    def removeWaiter(status: ChunkStatus, actor: ActorRef): Unit = {
      if (status.waitingChunk.contains(actor)) putStatus(status.copy(waitingChunk = status.waitingChunk - actor))
    }

    val isStorage = storages.contains(actor)
    if (isStorage) removeStorage(status, actor) else removeWaiter(status, actor)
  }
}
