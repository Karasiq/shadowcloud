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
    def enqueueRead(status: ChunkStatus): Option[StorageStatus] = {
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

    val statusOption = getChunkStatus(chunk)
    statusOption match {
      case Some(status) ⇒
        if (!Utils.isSameChunk(status.chunk, chunk)) {
          receiver ! ReadChunk.Failure(chunk, new IllegalArgumentException(s"Chunks conflict: ${status.chunk} / $chunk"))
        } else if (status.chunk.data.nonEmpty) {
          log.debug("Chunk extracted from cache: {}", status.chunk)
          receiver ! ReadChunk.Success(chunk, status.chunk)
        } else {
          readingChunks.addWaiter(chunk, receiver, () ⇒ enqueueRead(status))
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
            // context.watch(receiver)
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
        // context.watch(receiver)
        val status = ChunkStatus(WriteStatus.Pending(ChunkWriteAffinity.empty), Utils.timestamp, chunk, waitingChunk = Set(receiver))
        val affinity = storageSelector.forWrite(status)
        val statusWithAffinity = status.copy(WriteStatus.Pending(affinity))
        startWriteChunk(statusWithAffinity)
    }
  }

  def repairChunk(chunk: Chunk, newAffinity: Option[ChunkWriteAffinity], receiver: ActorRef)
                 (implicit storageSelector: StorageSelector): Option[ChunkStatus] = {

    val result = getChunkStatus(chunk).map { status ⇒
      status.writeStatus match {
        case WriteStatus.Pending(oldAffinity) ⇒
          val affinity = newAffinity.getOrElse(oldAffinity)
          val newStatus = status.copy(
            WriteStatus.Pending(affinity),
            waitingChunk = status.waitingChunk + receiver
          )
          startWriteChunk(newStatus)

        case WriteStatus.Finished ⇒
          if (!Utils.isSameChunk(status.chunk, chunk)) {
            receiver ! WriteChunk.Failure(chunk, new IllegalArgumentException(s"Chunk conflict: ${status.chunk} / $chunk"))
            status
          } else if (chunk.isEmpty) {
            receiver ! WriteChunk.Failure(chunk, new IllegalArgumentException("Chunk should not be empty"))
            status
          } else {
            val status1 = status.copy(
              WriteStatus.Pending(ChunkWriteAffinity.empty),
              chunk = chunk,
              waitingChunk = status.waitingChunk + receiver
            )
            val affinity = newAffinity.getOrElse(storageSelector.forWrite(status1))
            val status2 = status1.copy(WriteStatus.Pending(affinity))
            log.debug("Repairing chunk: {}", status2)
            startWriteChunk(status2)
          }
      }
    }

    if (result.isEmpty) receiver ! WriteChunk.Failure(chunk, new NoSuchElementException("Chunk not found"))
    result 
  }

  def retryPendingChunks()(implicit storageSelector: StorageSelector): Unit = {
    chunksMap.foreachValue {
      case chunkStatus @ ChunkStatus(WriteStatus.Pending(_), _, _, _, _) ⇒
        val affinity = storageSelector.forWrite(chunkStatus) // Try refresh affinity
        if (affinity.isFinished(chunkStatus)) {
          log.debug("Marking chunk as finished: {}", chunkStatus)
          putStatus(tryFinishChunk(chunkStatus))
        } else {
          startWriteChunk(chunkStatus)
        }

      case _ ⇒
        // Skip 
    }
  }

  // -----------------------------------------------------------------------
  // Update state
  // -----------------------------------------------------------------------
  def registerChunk(dispatcher: ActorRef, chunk: Chunk): ChunkStatus = {
    val storageId = storages.getStorageId(dispatcher)
    getChunkStatus(chunk) match {
      case Some(status) if !Utils.isSameChunk(status.chunk, chunk) ⇒
        log.error("Chunk conflict: {} / {}", status.chunk, chunk)
        status

      case Some(status)  ⇒
        status.writeStatus match {
          case WriteStatus.Pending(_) ⇒
            markAsWritten(status, storageId)

          case WriteStatus.Finished ⇒
            if (!status.availability.isWritten(storageId)) {
              log.debug("Chunk duplicate found on {}: {}", dispatcher, chunk)
              markAsWritten(status, storageId)
            } else {
              status
            }
        }

      case None ⇒
        putStatus(ChunkStatus(WriteStatus.Finished, Utils.timestamp,
          chunk.withoutData, ChunkAvailability.empty.withFinished(storageId)))
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

  def registerDiff(dispatcher: ActorRef, diff: ChunkIndexDiff)(implicit storageSelector: StorageSelector): Unit = {
    diff.deletedChunks.foreach(unregisterChunk(dispatcher, _))
    diff.newChunks.foreach(registerChunk(dispatcher, _))
  }

  // -----------------------------------------------------------------------
  // Callbacks
  // -----------------------------------------------------------------------
  def onReadSuccess(chunk: Chunk): Unit = {
    readingChunks.finish(chunk, ReadChunk.Success(chunk, chunk))
  }

  def onReadFailure(chunk: Chunk, storageId: String, error: Throwable): Unit = {
    readingChunks.finish(chunk, ReadChunk.Failure(chunk, error))
    getChunkStatus(chunk).foreach(markAsReadFailed(_, storageId))
  }

  def onWriteSuccess(chunk: Chunk, storageId: String): Unit = {
    registerChunk(storages.getDispatcher(storageId), chunk)
  }

  def onWriteFailure(chunk: Chunk, storageId: String, error: Throwable): Unit = {
    unregisterChunk(storages.getDispatcher(storageId), chunk)
    getChunkStatus(chunk).foreach(markAsWriteFailed(_, storageId))
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

  private[this] def tryFinishChunk(status: ChunkStatus): ChunkStatus = {
    status.writeStatus match {
      case WriteStatus.Pending(affinity) ⇒
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

      case WriteStatus.Finished ⇒
        status.waitingChunk.foreach(_ ! WriteChunk.Success(status.chunk, status.chunk))
        status.copy(waitingChunk = Set.empty)
    }
  }

  private[this] def markAsWriting(status: ChunkStatus, storageIds: String*): ChunkStatus = {
    val newStatus = status.copy(availability = status.availability.withWriting(storageIds: _*))
    putStatus(newStatus)
  }

  private[this] def markAsWritten(status: ChunkStatus, storageIds: String*): ChunkStatus = {
    val newStatus = status.copy(availability = status.availability.withFinished(storageIds:_*))
    putStatus(tryFinishChunk(newStatus))
  }

  private[this] def markAsWriteFailed(status: ChunkStatus, storageIds: String*): ChunkStatus = {
    val newStatus = status.copy(availability = status.availability.withWriteFailed(storageIds:_*))
    putStatus(newStatus)
  }

  private[this] def markAsReadFailed(status: ChunkStatus, storageIds: String*): ChunkStatus = {
    val newStatus = status.copy(availability = status.availability.withReadFailed(storageIds:_*))
    putStatus(newStatus)
  }

  private[this] def startWriteChunk(status: ChunkStatus)(implicit storageSelector: StorageSelector): ChunkStatus = {
    def enqueueWrites(status: ChunkStatus): Seq[StorageStatus] = {
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

    require(status.chunk.nonEmpty, "Chunk is empty")
    val writtenStorages = enqueueWrites(status)
    if (writtenStorages.isEmpty) {
      log.warning("No storages available for write: {}", status.chunk)
    } else {
      log.debug("Writing chunk to {}: {}", writtenStorages, status.chunk)
    }
    markAsWriting(status, writtenStorages.map(_.id):_*)
  }

  private[this] def removeStatus(status: ChunkStatus): Option[ChunkStatus] = {
    chunksMap.remove(status.chunk.checksum.hash)
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
