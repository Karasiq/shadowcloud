package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Kill, PossiblyHarmful, Props, Status, Terminated}
import akka.pattern.{ask, pipe}
import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
import com.karasiq.common.memory.SizeUnit
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.actors.context.RegionContext
import com.karasiq.shadowcloud.actors.events.{RegionEvents, StorageEvents}
import com.karasiq.shadowcloud.actors.internal.{ChunksTracker, RegionIndexTracker, StorageTracker}
import com.karasiq.shadowcloud.actors.messages.{RegionEnvelope, StorageEnvelope}
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.exceptions.{RegionException, StorageException}
import com.karasiq.shadowcloud.index.diffs.{ChunkIndexDiff, FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.index.{ChunkIndex, FolderIndex}
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.utils._
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus
import com.karasiq.shadowcloud.storage.replication.RegionStorageProvider.RegionStorage
import com.karasiq.shadowcloud.storage.replication.{ChunkWriteAffinity, StorageSelector}
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.streams.utils.AkkaStreamUtils
import com.karasiq.shadowcloud.utils.Utils

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object RegionDispatcher {
  // Messages
  sealed trait Message
  case class AttachStorage(storageId: StorageId, storageProps: StorageProps, dispatcher: ActorRef, health: StorageHealth = StorageHealth.empty)
      extends Message
  final case class DetachStorage(storageId: StorageId) extends Message
  case object GetStorages                              extends Message with MessageStatus[String, Seq[RegionStorage]]
  final case class GetChunkStatus(chunk: Chunk)        extends Message
  object GetChunkStatus                                extends MessageStatus[Chunk, ChunkStatus]
  case object GetHealth                                extends Message with MessageStatus[RegionId, RegionHealth]

  final case class WriteIndex(diff: FolderIndexDiff)                        extends Message
  object WriteIndex                                                         extends MessageStatus[FolderIndexDiff, IndexDiff]
  final case class GetChunkIndex(scope: IndexScope = IndexScope.default)    extends Message
  object GetChunkIndex                                                      extends MessageStatus[RegionId, ChunkIndex]
  final case class GetFolderIndex(scope: IndexScope = IndexScope.default)   extends Message
  object GetFolderIndex                                                     extends MessageStatus[RegionId, FolderIndex]
  final case class GetIndexSnapshot(scope: IndexScope = IndexScope.default) extends Message
  object GetIndexSnapshot                                                   extends MessageStatus[RegionId, IndexMerger.State[RegionKey]]

  case object Synchronize  extends Message with MessageStatus[RegionId, Map[StorageId, SyncReport]]
  case object CompactIndex extends Message

  final case class GetFiles(path: Path, scope: IndexScope = IndexScope.default)  extends Message
  object GetFiles                                                                extends MessageStatus[Path, Set[File]]
  final case class GetFolder(path: Path, scope: IndexScope = IndexScope.default) extends Message
  object GetFolder                                                               extends MessageStatus[Path, Folder]
  final case class GetFileAvailability(file: File)                               extends Message
  object GetFileAvailability                                                     extends MessageStatus[File, FileAvailability]

  final case class WriteChunk(chunk: Chunk)                                            extends Message
  case object WriteChunk                                                               extends MessageStatus[Chunk, Chunk]
  final case class ReadChunk(chunk: Chunk)                                             extends Message
  case object ReadChunk                                                                extends MessageStatus[Chunk, Chunk]
  final case class RewriteChunk(chunk: Chunk, newAffinity: Option[ChunkWriteAffinity]) extends Message

  // Internal messages
  private[actors] sealed trait InternalMessage                                                                              extends Message with PossiblyHarmful
  private[actors] final case class PushDiffs(storageId: StorageId, diffs: Seq[(SequenceNr, IndexDiff)], pending: IndexDiff) extends InternalMessage
  private[actors] final case class PullStorageIndex(storageId: StorageId)                                                   extends InternalMessage

  private[actors] final case class ChunkReadSuccess(storageId: Option[String], chunk: Chunk)                  extends InternalMessage
  private[actors] final case class ChunkReadFailed(storageId: Option[String], chunk: Chunk, error: Throwable) extends InternalMessage
  private[actors] final case class ChunkWriteSuccess(storageId: StorageId, chunk: Chunk)                      extends InternalMessage
  private[actors] final case class ChunkWriteFailed(storageId: StorageId, chunk: Chunk, error: Throwable)     extends InternalMessage
  private[actors] final case object RetryPendingChunks                                                        extends InternalMessage

  private[actors] final case class EnqueueIndexDiff(diff: IndexDiff) extends InternalMessage
  private[actors] final case class MarkAsPending(diff: IndexDiff)    extends InternalMessage
  private[actors] final case class WriteIndexDiff(diff: IndexDiff)   extends InternalMessage

  // Props
  def props(regionId: RegionId, regionProps: RegionConfig): Props = {
    Props(new RegionDispatcher(regionId, regionProps))
  }
}

//noinspection TypeAnnotation
private final class RegionDispatcher(regionId: RegionId, regionConfig: RegionConfig) extends Actor with ActorLogging {
  import RegionDispatcher._
  require(regionId.nonEmpty, "Invalid region identifier")

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] implicit val sc           = ShadowCloud()
  private[this] implicit val materializer = ActorMaterializer()
  import context.dispatcher
  import sc.implicits.defaultTimeout

  val storageTracker = StorageTracker()
  val chunksTracker  = ChunksTracker(regionId, regionConfig, storageTracker)
  val indexTracker   = RegionIndexTracker(regionId, chunksTracker)

  private[this] implicit val regionContext =
    RegionContext(regionId, regionConfig, self, storageTracker, chunksTracker.chunks, indexTracker.globalIndex)

  private[this] implicit val storageSelector = StorageSelector.fromClass(regionConfig.storageSelector)

  // -----------------------------------------------------------------------
  // Actors
  // -----------------------------------------------------------------------
  private[this] val gcActor = context.actorOf(RegionGC.props(regionId, regionConfig), "region-gc")

  // -----------------------------------------------------------------------
  // Streams
  // -----------------------------------------------------------------------
  private[this] val pendingIndexQueue = Source
    .queue[IndexDiff](sc.config.queues.regionDiffs, OverflowStrategy.dropNew)
    .via(AkkaStreamUtils.groupedOrInstant(sc.config.queues.regionDiffs, sc.config.queues.regionDiffsTime))
    .map(_.fold(IndexDiff.empty)((d1, d2) ⇒ d1.merge(d2)))
    .filter(_.nonEmpty)
    .log("region-grouped-diff")
    .map(WriteIndexDiff)
    .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
    .to(Sink.actorRef(self, Kill))
    .named("regionPendingQueue")
    .run()

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  def receive: Receive = {
    // -----------------------------------------------------------------------
    // Global index commands
    // -----------------------------------------------------------------------
    case WriteIndex(folders) ⇒
      if (folders.isEmpty) {
        sender() ! WriteIndex.Success(folders, indexTracker.indexes.pending)
        // sender() ! WriteIndex.Failure(folders, RegionException.IndexWriteFailed(IndexDiff(folders = folders), new IllegalArgumentException("Diff is empty")))
      } else {
        log.debug("Index write request: {}", folders)
        val future = (self ? EnqueueIndexDiff(IndexDiff(Utils.timestamp, folders))).mapTo[IndexDiff]
        WriteIndex.wrapFuture(folders, future).pipeTo(sender())
      }

    case EnqueueIndexDiff(diff) ⇒
      log.debug("Enqueuing region index diff: {}", diff)
      val currentSender = sender()
      pendingIndexQueue.offer(diff).onComplete {
        case Success(QueueOfferResult.Enqueued) ⇒
          self.tell(MarkAsPending(diff), currentSender)

        case Success(otherQueueResult) ⇒
          currentSender ! Status.Failure(RegionException.IndexWriteFailed(diff, new IllegalStateException(otherQueueResult.toString)))

        case Failure(error) ⇒
          currentSender ! Status.Failure(RegionException.IndexWriteFailed(diff, error))
      }

    case MarkAsPending(diff) ⇒
      indexTracker.indexes.markAsPending(diff)
      sender() ! Status.Success(indexTracker.indexes.pending)

    case WriteIndexDiff(diff) ⇒
      val storages = indexTracker.storages.io.writeIndex(diff)
      if (storages.isEmpty) {
        // Schedule retry
        schedules.writeDiff(diff)
      }

    case GetFiles(path, scope) ⇒
      val files = indexTracker.indexes.folders(scope).getFiles(path)
      if (files.nonEmpty) {
        sender() ! GetFiles.Success(path, files)
      } else {
        sender() ! GetFiles.Failure(path, RegionException.FileNotFound(path))
      }

    case GetFolder(path, scope) ⇒
      indexTracker.indexes.folders(scope).get(path) match {
        case Some(folder) ⇒
          sender() ! GetFolder.Success(path, folder)

        case None ⇒
          sender() ! GetFolder.Failure(path, RegionException.DirectoryNotFound(path))
      }

    case GetFolderIndex(scope) ⇒
      sender() ! GetFolderIndex.Success(regionId, indexTracker.indexes.folders(scope))

    case GetChunkIndex(scope) ⇒
      sender() ! GetChunkIndex.Success(regionId, indexTracker.indexes.chunks(scope))

    case GetIndexSnapshot(scope) ⇒
      sender() ! GetIndexSnapshot.Success(regionId, indexTracker.indexes.getState(scope))

    case GetFileAvailability(file) ⇒
      sender() ! GetFileAvailability.Success(file, indexTracker.indexes.getFileAvailability(file))

    case Synchronize ⇒
      log.info("Force synchronizing indexes of virtual region: {}", regionId)
      val futures = storageTracker.storages.map { storage ⇒
        indexTracker.storages.io.synchronize(storage).map((storage.id, _))
      }
      val result = Future.sequence(futures).map(_.toMap)
      Synchronize.wrapFuture(regionId, result).pipeTo(sender())

    case CompactIndex ⇒
      log.warning("Compacting region index: {}", regionId)
      storageTracker.storages.foreach(_.dispatcher ! StorageIndex.Envelope(regionId, RegionIndex.Compact))

    case GetChunkStatus(chunk) ⇒
      chunksTracker.chunks.getChunkStatus(chunk) match {
        case Some(status) ⇒
          sender() ! GetChunkStatus.Success(chunk, status)

        case None ⇒
          sender() ! GetChunkStatus.Failure(chunk, RegionException.ChunkNotFound(chunk))
      }

    // -----------------------------------------------------------------------
    // Read/write commands
    // -----------------------------------------------------------------------
    case ReadChunk(chunk) ⇒
      chunksTracker.chunkIO.readChunk(chunk, sender())

    case WriteChunk(chunk) ⇒
      gcActor ! RegionGC.Reserve(Set(chunk.withoutData))
      chunksTracker.chunkIO.writeChunk(chunk, sender())

    case RewriteChunk(chunk, newAffinity) ⇒
      gcActor ! RegionGC.Reserve(Set(chunk.withoutData))
      chunksTracker.chunkIO.repairChunk(chunk, newAffinity, sender())

    case ChunkReadSuccess(storageId, chunk) ⇒
      chunksTracker.storages.callbacks.onReadSuccess(chunk, storageId)

    case ChunkReadFailed(storageId, chunk, error) ⇒
      chunksTracker.storages.callbacks.onReadFailure(chunk, storageId, error)

    case ChunkWriteSuccess(storageId, chunk) ⇒
      chunksTracker.storages.callbacks.onWriteSuccess(chunk, storageId)

    case ChunkWriteFailed(storageId, chunk, error) ⇒
      chunksTracker.storages.callbacks.onWriteFailure(chunk, storageId, error)
      schedules.scheduleRetry()

    case RetryPendingChunks ⇒
      if (schedules.shouldRetry) {
        schedules.cancelRetry()
        log.debug("Retrying pending chunks")
        chunksTracker.chunkIO.retryPendingChunks()
      }

    // -----------------------------------------------------------------------
    // Storage events
    // -----------------------------------------------------------------------
    case AttachStorage(storageId, props, dispatcher, health) ⇒
      val isStorageExists = storageTracker.contains(storageId)
      if (isStorageExists && storageTracker.getDispatcher(storageId) == dispatcher) {
        // Ignore
      } else {
        if (isStorageExists) {
          log.warning("Replacing storage {} dispatcher: {}", storageId, dispatcher)
          indexTracker.storages.state.dropStorageDiffs(storageId)
          storageTracker.unregister(storageId)
          chunksTracker.storages.state.unregister(storageId)
        }

        log.info("Attaching storage {}: {}", storageId, dispatcher)
        storageTracker.register(storageId, props, dispatcher, health)
        self ! PullStorageIndex(storageId)
      }

    case DetachStorage(storageId) if storageTracker.contains(storageId) ⇒
      log.warning("Detaching storage: {}", storageId)
      chunksTracker.storages.state.unregister(storageId)
      indexTracker.storages.state.dropStorageDiffs(storageId)
      storageTracker.unregister(storageId)

    case GetStorages ⇒
      sender() ! GetStorages.Success(regionId, storageTracker.storages)

    case GetHealth ⇒
      val regionHealth = chunksTracker.storages.state.getHealth()
      sender() ! GetHealth.Success(regionId, regionHealth)

    case PullStorageIndex(storageId) if storageTracker.contains(storageId) ⇒
      schedules.deferGC()
      val storage = storageTracker.getStorage(storageId)

      storage.dispatcher ! StorageIndex.OpenIndex(regionId)
      storage.dispatcher ! StorageDispatcher.GetHealth(true)

      val indexFuture = indexTracker.storages.io
        .synchronize(storage)
        .flatMap(_ => indexTracker.storages.io.getIndex(storage))

      indexFuture.onComplete {
        case Success(IndexMerger.State(Nil, IndexDiff.empty)) | Failure(StorageException.NotFound(_)) ⇒
          log.info("Copying index to {}", storageId)
          val newDiff = indexTracker.indexes.toMergedDiff
            .copy(chunks = ChunkIndexDiff.empty)
            .creates
          if (newDiff.nonEmpty) {
            indexTracker.storages.io.writeIndex(storage, newDiff)
            indexTracker.storages.io.synchronize(storage)
          }

        case Success(IndexMerger.State(diffs, pending)) ⇒
          log.debug("Storage {} index fetched: {} ({})", storageId, diffs, pending)
          self ! PushDiffs(storageId, diffs, pending)

        case Failure(error) ⇒
          log.error(error, "Error fetching index from storage: {}", storageId)
          schedules.pullIndex(storageId)
      }

    case PushDiffs(storageId, diffs, pending) if storageTracker.contains(storageId) ⇒
      indexTracker.indexes.markAsPending(pending)
      indexTracker.storages.state.addStorageDiffs(storageId, diffs)

    case StorageEnvelope(storageId, event: StorageEvents.Event) if storageTracker.contains(storageId) ⇒
      event match {
        case StorageEvents.IndexLoaded(`regionId`, state) ⇒
          log.info("Storage [{}] index loaded: {} diffs", storageId, state.diffs.length)
          indexTracker.storages.state.dropStorageDiffs(storageId)
          indexTracker.storages.state.addStorageDiffs(storageId, state.diffs)
          val deletedChunks = {
            val oldIndex = indexTracker.storages.state.extractIndex(storageId)
            val newIndex = IndexMerger.restore(state)
            newIndex.chunks.diff(oldIndex.chunks).deletedChunks
          }
          deletedChunks.foreach(chunksTracker.storages.state.unregisterChunk(storageId, _))
          schedules.deferGC()

        case StorageEvents.IndexUpdated(`regionId`, sequenceNr, diff, _) ⇒
          log.debug("Storage [{}] index updated: {}", storageId, diff)
          indexTracker.storages.state.addStorageDiff(storageId, sequenceNr, diff)
          schedules.deferGC()
          gcActor ! RegionGC.UnReserve(diff.folders.folders.flatMap(_.newFiles).flatMap(_.chunks).toSet)

        case StorageEvents.PendingIndexUpdated(`regionId`, diff) ⇒
          log.debug("Storage [{}] pending index updated: {}", storageId, diff)
          // globalIndex.addPending(diff)

        case StorageEvents.IndexDeleted(`regionId`, sequenceNrs) ⇒
          log.debug("Diffs deleted from storage [{}]: {}", storageId, sequenceNrs)
          indexTracker.storages.state.dropStorageDiffs(storageId, sequenceNrs)

        case StorageEvents.ChunkWritten(ChunkPath(`regionId`, _), chunk) ⇒
          log.info("Chunk written: {}", chunk)
          // chunks.onWriteSuccess(chunk, storageId)
          indexTracker.indexes.registerChunk(chunk)
          sc.eventStreams.publishRegionEvent(regionId, RegionEvents.ChunkWritten(storageId, chunk))
          schedules.deferGC()

        case StorageEvents.HealthUpdated(health) ⇒
          log.debug("Storage [{}] health report: {}", storageId, health)
          val wasOffline = {
            val oldHealth = storageTracker.getStorage(storageId).health
            (!oldHealth.online || oldHealth.writableSpace < SizeUnit.MB) &&
            (health.online && health.writableSpace > SizeUnit.MB)
          }
          storageTracker.updateHealth(storageId, health)
          if (wasOffline) schedules.scheduleRetry()

        case _ ⇒
        // Ignore
      }

    case Terminated(dispatcher) ⇒
      log.debug("Watched actor terminated: {}", dispatcher)
      if (storageTracker.contains(dispatcher)) {
        val storageId = storageTracker.getStorageId(dispatcher)
        indexTracker.storages.state.dropStorageDiffs(storageId)
        storageTracker.unregister(storageId)
        chunksTracker.storages.state.unregister(storageId)
      } else {
        chunksTracker.storages.state.unregister(dispatcher)
      }

    // -----------------------------------------------------------------------
    // GC commands
    // -----------------------------------------------------------------------
    case m: RegionGC.Message ⇒
      gcActor.forward(m)
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  object schedules {
    private[this] val scheduler = context.system.scheduler
    var shouldRetry             = false

    def pullIndex(storageId: StorageId): Unit = {
      scheduler.scheduleOnce(5 seconds, self, PullStorageIndex(storageId))
    }

    def writeDiff(diff: IndexDiff): Unit = {
      scheduler.scheduleOnce(15 seconds, sc.actors.regionSupervisor, RegionEnvelope(regionId, WriteIndexDiff(diff)))
    }

    def deferGC(): Unit = {
      gcActor ! RegionGC.Defer(10 minutes)
    }

    def scheduleRetry(): Unit = {
      shouldRetry = true
    }

    def cancelRetry(): Unit = {
      shouldRetry = false
    }

    private[RegionDispatcher] def initSchedules(): Unit = {
      scheduler.schedule(30 seconds, 3 seconds, self, RetryPendingChunks)
    }
  }

  // -----------------------------------------------------------------------
  // Lifecycle
  // -----------------------------------------------------------------------
  override def preStart(): Unit = {
    super.preStart()
    schedules.initSchedules()
  }

  override def postStop(): Unit = {
    sc.eventStreams.storage.unsubscribe(self)
    pendingIndexQueue.complete()
    super.postStop()
  }
}
