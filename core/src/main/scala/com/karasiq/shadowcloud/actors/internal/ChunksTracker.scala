package com.karasiq.shadowcloud.actors.internal

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.{implicitConversions, postfixOps}

import akka.actor.{ActorContext, ActorRef}
import akka.event.Logging
import akka.pattern.{ask, pipe}
import akka.util.{ByteString, Timeout}

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.actors.RegionDispatcher._
import com.karasiq.shadowcloud.actors.internal.ChunksTracker.ChunkReadStatus
import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.exceptions.{RegionException, SCExceptions}
import com.karasiq.shadowcloud.index.diffs.ChunkIndexDiff
import com.karasiq.shadowcloud.model.{Chunk, ChunkId, RegionId, StorageId}
import com.karasiq.shadowcloud.storage.replication.{ChunkAvailability, ChunkStatusProvider, ChunkWriteAffinity, StorageSelector}
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.{ChunkStatus, WriteStatus}
import com.karasiq.shadowcloud.storage.replication.RegionStorageProvider.RegionStorage
import com.karasiq.shadowcloud.utils.Utils

private[actors] object ChunksTracker {
  def apply(regionId: RegionId, config: RegionConfig, storageTracker: StorageTracker)
           (implicit context: ActorContext, sc: ShadowCloudExtension): ChunksTracker = {
    new ChunksTracker(regionId, config, storageTracker)
  }

  case class ChunkReadStatus(reading: Set[String], waiting: Set[ActorRef])
}

// Internal region logic
private[actors] final class ChunksTracker(regionId: RegionId, config: RegionConfig, storageTracker: StorageTracker)
                                         (implicit context: ActorContext, sc: ShadowCloudExtension) {
  import context.dispatcher

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] implicit val sender: ActorRef = context.self
  private[this] val log = Logging(sc.implicits.actorSystem, s"$regionId-chunks")
  private[this] val chunksMap = mutable.AnyRefMap[ByteString, ChunkStatus]()
  private[this] val readingChunks = mutable.AnyRefMap[Chunk, ChunkReadStatus]()

  // -----------------------------------------------------------------------
  // Read/write
  // -----------------------------------------------------------------------
  object chunkIO {
    def readChunk(chunk: Chunk, receiver: ActorRef)(implicit storageSelector: StorageSelector): Option[ChunkStatus] = {
      def getReadFuture(status: ChunkStatus): (Option[RegionStorage], Future[Chunk]) = {
        implicit val readTimeout: Timeout = sc.config.timeouts.chunkRead

        if (status.chunk.data.nonEmpty) {
          log.debug("Chunk extracted from cache: {}", status.chunk)
          (None, Future.successful(status.chunk))
        } else {
          val chunk = status.chunk.withoutData
          val storage = storageSelector.forRead(status)
          storage match {
            case Some(storage) ⇒
              log.debug("Reading chunk from {}: {}", storage.id, chunk)
              (Some(storage), storages.io.readChunk(storage, chunk))

            case None ⇒
              (None, Future.failed(RegionException.ChunkReadFailed(chunk, RegionException.ChunkUnavailable(chunk))))
          }
        }
      }

      def pipeReadFuture(storageId: Option[String], chunk: Chunk, future: Future[Chunk]): Unit = {
        future
          .map(ChunkReadSuccess(storageId, _))
          .recover { case error ⇒ ChunkReadFailed(storageId, chunk, error) }
          .pipeTo(context.self)
      }

      def addReader(chunk: Chunk, storageId: StorageId): Unit = {
        val status = readingChunks.getOrElse(chunk, ChunkReadStatus(Set.empty, Set.empty))
        readingChunks += chunk → status.copy(reading = status.reading + storageId)
      }

      def addWaiter(chunk: Chunk, receiver: ActorRef): Unit = {
        if (receiver == ActorRef.noSender) return
        val status = readingChunks.getOrElse(chunk, ChunkReadStatus(Set.empty, Set.empty))
        readingChunks += chunk → status.copy(waiting = status.waiting + receiver)
      }

      val statusOption = chunks.getChunkStatus(chunk)
      statusOption match {
        case Some(status) ⇒
          /* if (!Utils.isSameChunk(status.chunk, chunk)) {
            receiver ! ReadChunk.Failure(chunk, new IllegalArgumentException(s"Chunks conflict: ${status.chunk} / $chunk"))
          } */

          val actualChunk = status.chunk.withoutData
          val readStatus = readingChunks.getOrElse(actualChunk, ChunkReadStatus(Set.empty, Set.empty))
          if (readStatus.reading.isEmpty) {
            val (storageOption, future) = getReadFuture(status)
            storageOption.foreach(storage ⇒ addReader(actualChunk, storage.id))
            pipeReadFuture(storageOption.map(_.id), actualChunk, future)
          }
          addWaiter(actualChunk, receiver)

        case None ⇒
          receiver ! ReadChunk.Failure(chunk, RegionException.ChunkReadFailed(chunk, RegionException.ChunkNotFound(chunk)))
      }
      statusOption
    }

    def writeChunk(chunk: Chunk, receiver: ActorRef)(implicit storageSelector: StorageSelector): ChunkStatus = {
      require(chunk.nonEmpty, "Chunk data is empty")
      chunks.getChunkStatus(chunk) match {
        case Some(status) ⇒
          status.writeStatus match {
            case WriteStatus.Pending(_) ⇒
              // context.watch(receiver)
              log.debug("Already writing chunk, added to queue: {}", chunk)
              chunks.update(status.copy(waitingChunk = status.waitingChunk + receiver))

            case WriteStatus.Finished ⇒
              val chunkWithData = if (Utils.isSameChunk(status.chunk, chunk) && chunk.data.encrypted.nonEmpty) {
                chunk
              } else {
                status.chunk.copy(data = chunk.data.copy(encrypted = ByteString.empty))
              }
              receiver ! WriteChunk.Success(chunkWithData.withoutData, chunkWithData)
              log.debug("Chunk restored from index, write skipped: {}", chunkWithData)
              status.copy(chunk = chunkWithData)
          }

        case None ⇒
          // context.watch(receiver)
          val status = ChunkStatus(WriteStatus.Pending(ChunkWriteAffinity.empty), chunk, waitingChunk = Set(receiver))
          val affinity = storageSelector.forWrite(status)
          val statusWithAffinity = status.copy(WriteStatus.Pending(affinity))
          startWriteChunk(statusWithAffinity)
      }
    }

    def repairChunk(chunk: Chunk, newAffinity: Option[ChunkWriteAffinity], receiver: ActorRef)
                   (implicit storageSelector: StorageSelector): Option[ChunkStatus] = {

      val result = chunks.getChunkStatus(chunk).map { status ⇒
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
              receiver ! WriteChunk.Failure(chunk, RegionException.ChunkRepairFailed(chunk, SCExceptions.ChunkConflict(status.chunk, chunk)))
              status
            } else if (chunk.isEmpty) {
              receiver ! WriteChunk.Failure(chunk, RegionException.ChunkRepairFailed(chunk, SCExceptions.ChunkDataIsEmpty(chunk)))
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

      if (result.isEmpty) receiver ! WriteChunk.Failure(chunk, RegionException.ChunkRepairFailed(chunk, RegionException.ChunkNotFound(chunk)))
      result
    }

    def retryPendingChunks()(implicit storageSelector: StorageSelector): Unit = {
      chunksMap.foreachValue {
        case chunkStatus @ ChunkStatus(WriteStatus.Pending(affinity), _, availability, _) if availability.writeFailed.nonEmpty ⇒
          val newAffinity = if (affinity.isFailed(chunkStatus)) {
            storageSelector.forWrite(chunkStatus) // Try refresh affinity
          } else {
            affinity
          }

          val newStatus = chunkStatus.copy(WriteStatus.Pending(newAffinity),
            availability = availability.copy(writeFailed = Set.empty))
          if (newAffinity.isFinished(chunkStatus)) {
            log.debug("Marking chunk as finished: {}", chunkStatus)
            chunks.update(tryFinishChunk(newStatus))
          } else {
            startWriteChunk(newStatus)
          }

        case _ ⇒
        // Skip
      }
    }

    private[ChunksTracker] def tryFinishChunk(status: ChunkStatus): ChunkStatus = {
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

    private[this] def startWriteChunk(status: ChunkStatus)(implicit storageSelector: StorageSelector): ChunkStatus = {
      def enqueueWrites(status: ChunkStatus): Seq[(RegionStorage, Future[Chunk])] = {
        implicit val timeout: Timeout = sc.config.timeouts.chunkWrite

        val storageIds = status.writeStatus match {
          case WriteStatus.Pending(affinity) ⇒
            affinity.selectForWrite(status)

          case WriteStatus.Finished ⇒
            Vector.empty
        }
        val selectedStorages = storageIds.map(storageTracker.getStorage)
        selectedStorages.map(storage ⇒ (storage, storages.io.writeChunk(storage, status.chunk)))
      }

      require(status.chunk.nonEmpty, "Chunk is empty")
      val writes = enqueueWrites(status)
      val writtenStorages = writes.map(_._1)
      if (writtenStorages.isEmpty) {
        log.warning("No storages available for write: {}", status.chunk)
        status
      } else {
        log.debug("Writing chunk to {}: {}", writtenStorages, status.chunk)
        writes.foreach { case (storage, future) ⇒
          future
            .map(ChunkWriteSuccess(storage.id, _))
            .recover { case error ⇒ ChunkWriteFailed(storage.id, status.chunk, error) }
            .pipeTo(context.self)
        }
        storages.state.markAsWriting(status, writtenStorages.map(_.id):_*)
      }
    }
  }

  // -----------------------------------------------------------------------
  // Chunks state
  // -----------------------------------------------------------------------
  object chunks extends ChunkStatusProvider {
    override def getChunkStatus(chunk: Chunk): Option[ChunkStatus] = {
      chunksMap.get(toChunkMapKey(chunk))
    }

    override def getChunkStatusList(): Iterable[ChunkStatus] = {
      chunksMap.values
    }

    private[ChunksTracker] def update(status: ChunkStatus): ChunkStatus = {
      assert(status.writeStatus != WriteStatus.Finished || status.chunk.data.isEmpty)
      chunksMap += toChunkMapKey(status.chunk) → status
      status
    }

    private[ChunksTracker] def delete(status: ChunkStatus): Option[ChunkStatus] = {
      chunksMap.remove(toChunkMapKey(status.chunk))
    }

    @inline
    private[this] def toChunkMapKey(chunk: Chunk): ChunkId = {
      chunk.checksum.hash
    }
  }

  // -----------------------------------------------------------------------
  // Storage dispatchers logic
  // -----------------------------------------------------------------------
  object storages {
    object io {
      import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ChunkPath, ReadChunk ⇒ SReadChunk, WriteChunk ⇒ SWriteChunk}

      def writeChunk(storage: RegionStorage, chunk: Chunk) = {
        implicit val timeout: Timeout = sc.config.timeouts.chunkWrite
        SWriteChunk.unwrapFuture(storage.dispatcher ? SWriteChunk(getChunkPath(storage, chunk), chunk))
      }

      def readChunk(storage: RegionStorage, chunk: Chunk): Future[Chunk] = {
        implicit val timeout: Timeout = sc.config.timeouts.chunkRead
        SReadChunk.unwrapFuture(storage.dispatcher ? SReadChunk(getChunkPath(storage, chunk), chunk))
      }

      private[this] def getChunkPath(storage: RegionStorage, chunk: Chunk): ChunkPath = {
        ChunkPath(regionId, storage.config.chunkKey(chunk))
      }
    }

    object state {
      def registerChunk(storageId: StorageId, chunk: Chunk): ChunkStatus = {
        chunks.getChunkStatus(chunk) match {
          case Some(status) if !Utils.isSameChunk(status.chunk, chunk) ⇒
            if (status.availability.isWriting(storageId)) {
              log.warning("Replacing {} with {} on {}", status.chunk, chunk, storageId)
              unregisterChunk(storageId, status.chunk)
              assert(chunks.getChunkStatus(chunk).isEmpty)
              registerChunk(storageId, chunk)
            } else {
              log.error("Chunk conflict: {} / {}", status.chunk, chunk)
              status
            }

          case Some(status)  ⇒
            storages.state.markAsWritten(status, storageId)

          case None ⇒
            chunks.update(ChunkStatus(WriteStatus.Finished, chunk.withoutData, ChunkAvailability.empty.withFinished(storageId)))
        }
      }

      def unregisterChunk(storageId: StorageId, chunk: Chunk): Unit = {
        chunks.getChunkStatus(chunk) match {
          case Some(status) ⇒
            if (Utils.isSameChunk(status.chunk, chunk)) {
              storages.state.markAsLost(status, storageId)
            } else {
              log.warning("Unknown chunk deleted: {} (existing: {})", chunk, status.chunk)
            }

          case None ⇒
            log.debug("Chunk not found: {}", chunk)
        }
      }

      def unregister(storageId: StorageId): Unit = {
        chunksMap.foreachValue(markAsLost(_, storageId))
      }

      def unregister(dispatcher: ActorRef): Unit = {
        def removeWaiter(status: ChunkStatus, actor: ActorRef): Unit = {
          if (status.waitingChunk.contains(actor))
            chunks.update(status.copy(waitingChunk = status.waitingChunk - actor))

          readingChunks.get(status.chunk).foreach { readStatus ⇒
            if (readStatus.waiting.contains(actor))
              readingChunks += status.chunk → readStatus.copy(waiting = readStatus.waiting - actor)
          }
        }

        if (storageTracker.contains(dispatcher)) {
          val storageId = storageTracker.getStorageId(dispatcher)
          unregister(storageId)
        } else {
          chunksMap.foreachValue(removeWaiter(_, dispatcher))
          readingChunks.foreach { case (chunk, status) ⇒
            if (status.waiting.contains(dispatcher)) {
              val newStatus = status.copy(waiting = status.waiting - dispatcher)
              if (newStatus.waiting.isEmpty) {
                log.warning("Cancelling chunk read: {}", chunk)
                readingChunks -= chunk
              } else {
                readingChunks += chunk → newStatus
              }
            }
          }
        }
      }

      def registerDiff(storageId: StorageId, diff: ChunkIndexDiff): Unit = {
        diff.deletedChunks.foreach(unregisterChunk(storageId, _))
        diff.newChunks.foreach(registerChunk(storageId, _))
      }

      private[ChunksTracker] def markAsWritten(status: ChunkStatus, storageIds: String*): ChunkStatus = {
        val newStatus = status.copy(availability = status.availability.withFinished(storageIds:_*))
        chunks.update(chunkIO.tryFinishChunk(newStatus))
      }

      private[ChunksTracker] def markAsWriteFailed(status: ChunkStatus, storageIds: String*): ChunkStatus = {
        val newStatus = status.copy(availability = status.availability.withWriteFailed(storageIds:_*))
        chunks.update(newStatus)
      }

      private[ChunksTracker] def markAsReadFailed(status: ChunkStatus, storageIds: String*): ChunkStatus = {
        val newStatus = status.copy(availability = status.availability.withReadFailed(storageIds:_*))
        chunks.update(newStatus)
      }

      private[ChunksTracker] def markAsWriting(status: ChunkStatus, storageIds: String*): ChunkStatus = {
        val newStatus = status.copy(availability = status.availability.withWriting(storageIds: _*))
        chunks.update(newStatus)
      }

      private[ChunksTracker] def markAsLost(status: ChunkStatus, storageId: StorageId): Unit = {
        /* val dispatcher = storages.getDispatcher(storageId) */
        if (status.availability.contains(storageId) /* || status.waitingChunk.contains(dispatcher) */) {
          val newStatus = status.copy(
            availability = status.availability - storageId /* ,
            waitingChunk = status.waitingChunk - dispatcher */
          )

          status.writeStatus match {
            case WriteStatus.Pending(_) ⇒
              chunks.update(newStatus)
              // retryPendingChunks()

            case WriteStatus.Finished ⇒
              if (newStatus.availability.isEmpty) {
                log.debug("Chunk is lost: {}", newStatus.chunk)
                chunks.delete(status)
              }
          }
        }
      }
    }

    object callbacks {
      def onReadSuccess(chunk: Chunk, storageId: Option[StorageId]): Unit = {
        log.debug("Read success from {}: {}", storageId.getOrElse("<internal>"), chunk)
        readingChunks.remove(chunk) match {
          case Some(ChunkReadStatus(_, waiting)) ⇒
            waiting.foreach(_ ! ReadChunk.Success(chunk.withoutData, chunk))

          case None ⇒
          // Ignore
        }
      }

      def onReadFailure(chunk: Chunk, storageId: Option[StorageId], error: Throwable)
                       (implicit storageSelector: StorageSelector): Unit = {

        log.error(error, "Chunk read failure from {}: {}", storageId.getOrElse("<internal>"), chunk)

        for (storageId ← storageId; status ← chunks.getChunkStatus(chunk)) {
          state.markAsReadFailed(status, storageId)
        }

        readingChunks.get(chunk) match {
          case Some(ChunkReadStatus(reading, waiting)) ⇒
            def cancelChunkRead(): Unit = {
              log.warning("Cancelling chunk read: {}", chunk)
              waiting.foreach(_ ! ReadChunk.Failure(chunk, RegionException.ChunkReadFailed(chunk, error)))
              readingChunks -= chunk
            }

            if (waiting.isEmpty) {
              cancelChunkRead()
            } else {
              val newReading = reading -- storageId
              readingChunks += chunk → ChunkReadStatus(newReading, waiting)
              if (newReading.isEmpty) {
                if (storageId.nonEmpty) {
                  // Retry
                  chunkIO.readChunk(chunk, ActorRef.noSender)
                } else {
                  // No storages left
                  cancelChunkRead()
                }
              }
            }

          case None ⇒
            // Ignore
        }
      }

      def onWriteSuccess(chunk: Chunk, storageId: StorageId): Unit = {
        log.debug("Chunk write success to {}: {}", storageId, chunk)
        state.registerChunk(storageId, chunk)
      }

      def onWriteFailure(chunk: Chunk, storageId: StorageId, error: Throwable): Unit = {
        log.error(error, "Chunk write failed from {}: {}", storageId, chunk)
        state.unregisterChunk(storageId, chunk)
        chunks.getChunkStatus(chunk).foreach(state.markAsWriteFailed(_, storageId))
      }
    }
  }
}
