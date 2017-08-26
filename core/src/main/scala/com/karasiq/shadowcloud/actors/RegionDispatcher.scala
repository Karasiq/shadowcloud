package com.karasiq.shadowcloud.actors

import java.io.FileNotFoundException

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorLogging, ActorRef, Kill, PossiblyHarmful, Props, Status, Terminated}
import akka.pattern.{ask, pipe}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ChunkPath, ReadChunk ⇒ SReadChunk, WriteChunk ⇒ SWriteChunk}
import com.karasiq.shadowcloud.actors.context.RegionContext
import com.karasiq.shadowcloud.actors.events.{RegionEvents, StorageEvents}
import com.karasiq.shadowcloud.actors.internal.{ChunksTracker, StorageTracker}
import com.karasiq.shadowcloud.actors.messages.{RegionEnvelope, StorageEnvelope}
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.actors.RegionIndex.WriteDiff
import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.model.{RegionId, SequenceNr, StorageId}
import com.karasiq.shadowcloud.storage.StorageHealth
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.replication.{ChunkWriteAffinity, StorageSelector}
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus
import com.karasiq.shadowcloud.storage.replication.RegionStorageProvider.RegionStorage
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.utils.{AkkaStreamUtils, MemorySize, Utils}

object RegionDispatcher {
  // Messages
  sealed trait Message
  case class AttachStorage(storageId: StorageId, storageProps: StorageProps,
                           dispatcher: ActorRef, health: StorageHealth = StorageHealth.empty) extends Message
  case class DetachStorage(storageId: StorageId) extends Message
  case object GetStorages extends Message with MessageStatus[String, Seq[RegionStorage]]
  case class GetChunkStatus(chunk: Chunk) extends Message
  object GetChunkStatus extends MessageStatus[Chunk, ChunkStatus]

  case class WriteIndex(diff: FolderIndexDiff) extends Message
  object WriteIndex extends MessageStatus[FolderIndexDiff, IndexDiff]
  case object GetIndex extends Message with MessageStatus[String, IndexMerger.State[RegionKey]]
  case object Synchronize extends Message
  case class GetFiles(path: Path) extends Message
  object GetFiles extends MessageStatus[Path, Set[File]]
  case class GetFolder(path: Path) extends Message
  object GetFolder extends MessageStatus[Path, Folder]

  case class WriteChunk(chunk: Chunk) extends Message
  case object WriteChunk extends MessageStatus[Chunk, Chunk]
  case class ReadChunk(chunk: Chunk) extends Message
  case object ReadChunk extends MessageStatus[Chunk, Chunk]
  case class RewriteChunk(chunk: Chunk, newAffinity: Option[ChunkWriteAffinity]) extends Message

  // Internal messages
  private[actors] sealed trait InternalMessage extends Message with PossiblyHarmful
  private[actors] case class PushDiffs(storageId: StorageId, diffs: Seq[(Long, IndexDiff)], pending: IndexDiff) extends InternalMessage
  private[actors] case class PullStorageIndex(storageId: StorageId) extends InternalMessage

  private[actors] case class ChunkReadSuccess(storageId: Option[String], chunk: Chunk) extends InternalMessage
  private[actors] case class ChunkReadFailed(storageId: Option[String], chunk: Chunk, error: Throwable) extends InternalMessage
  private[actors] case class ChunkWriteSuccess(storageId: StorageId, chunk: Chunk) extends InternalMessage
  private[actors] case class ChunkWriteFailed(storageId: StorageId, chunk: Chunk, error: Throwable) extends InternalMessage

  private[actors] case class EnqueueIndexDiff(diff: IndexDiff) extends InternalMessage
  private[actors] case class MarkAsPending(diff: IndexDiff) extends InternalMessage
  private[actors] case class WriteIndexDiff(diff: IndexDiff) extends InternalMessage

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
  private[this] implicit val executionContext: ExecutionContext = context.dispatcher
  private[this] implicit val timeout = Timeout(3 seconds)
  private[this] implicit val materializer: Materializer = ActorMaterializer()
  private[this] val sc = ShadowCloud()
  private[this] val storages = StorageTracker()
  private[this] val chunks = ChunksTracker(regionId, regionConfig, storages, log)
  private[this] val globalIndex = IndexMerger.region
  private[this] implicit val regionContext = RegionContext(regionId, regionConfig, self, storages, chunks, globalIndex)
  private[this] implicit val storageSelector = StorageSelector.fromClass(regionConfig.storageSelector)

  // -----------------------------------------------------------------------
  // Actors
  // -----------------------------------------------------------------------
  private[this] val gcActor = context.actorOf(RegionGC.props(regionId, regionConfig.garbageCollector), "region-gc")

  // -----------------------------------------------------------------------
  // Streams
  // -----------------------------------------------------------------------
  private[this] val pendingIndexQueue = Source.queue[IndexDiff](sc.config.queues.regionDiffs, OverflowStrategy.dropNew)
    .via(AkkaStreamUtils.groupedOrInstant(sc.config.queues.regionDiffs, sc.config.queues.regionDiffsTime))
    .map(_.fold(IndexDiff.empty)((d1, d2) ⇒ d1.merge(d2)))
    .filter(_.nonEmpty)
    .log("region-grouped-diff")
    .map(WriteIndexDiff)
    .to(Sink.actorRef(self, Kill))
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
        sender() ! WriteIndex.Failure(folders, new IllegalArgumentException("Diff is empty"))
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
          currentSender ! Status.Failure(new IllegalStateException(otherQueueResult.toString))

        case Failure(error) ⇒
          currentSender ! Status.Failure(error)
      }

    case MarkAsPending(diff) ⇒
      globalIndex.addPending(diff)
      sender() ! Status.Success(globalIndex.pending)

    case WriteIndexDiff(diff) ⇒
      log.debug("Writing region index diff: {}", diff)
      val storages = storageSelector.forIndexWrite(diff)
      if (storages.isEmpty) {
        log.warning("No index storages available on {}", regionId)
        context.system.scheduler.scheduleOnce(15 seconds, sc.actors.regionSupervisor, RegionEnvelope(regionId, WriteIndexDiff(diff)))
      } else {
        log.debug("Writing to virtual region [{}] index: {} (storages = {})", regionId, diff, storages)
        storages.foreach(_.dispatcher ! StorageIndex.Envelope(regionId, WriteDiff(diff)))
      }

    case GetFiles(path) ⇒
      val files = folderIndex
        .get(path.parent)
        .map(_.files.filter(_.path == path))
        .filter(_.nonEmpty)

      files match {
        case Some(files) ⇒
          sender() ! GetFiles.Success(path, files)

        case None ⇒
          sender() ! GetFiles.Failure(path, new FileNotFoundException(s"No such file: $path"))
      }

    case GetFolder(path) ⇒
      folderIndex.get(path) match {
        case Some(folder) ⇒
          sender() ! GetFolder.Success(path, folder)

        case None ⇒
          sender() ! GetFolder.Failure(path, new FileNotFoundException(s"No such directory: $path"))
      }

    case GetIndex ⇒
      sender() ! GetIndex.Success(regionId, IndexMerger.state(globalIndex))

    case Synchronize ⇒
      log.info("Force synchronizing indexes of virtual region: {}", regionId)
      storages.storages.foreach(_.dispatcher ! StorageIndex.Envelope(regionId, RegionIndex.Synchronize))

    case GetChunkStatus(chunk) ⇒
      chunks.getChunkStatus(chunk) match {
        case Some(status) ⇒
          sender() ! GetChunkStatus.Success(chunk, status)

        case None ⇒
          sender() ! GetChunkStatus.Failure(chunk, new NoSuchElementException("Chunk not found"))
      }

    // -----------------------------------------------------------------------
    // Read/write commands
    // -----------------------------------------------------------------------
    case ReadChunk(chunk) ⇒
      chunks.readChunk(chunk, sender())

    case WriteChunk(chunk) ⇒
      chunks.writeChunk(chunk, sender())

    case RewriteChunk(chunk, newAffinity) ⇒
      chunks.repairChunk(chunk, newAffinity, sender())

    case ChunkReadSuccess(storageId, chunk) ⇒
      chunks.onReadSuccess(chunk, storageId)

    case ChunkReadFailed(storageId, chunk, error) ⇒
      chunks.onReadFailure(chunk, storageId, error)

    case ChunkWriteSuccess(storageId, chunk) ⇒
      log.debug("Chunk write success: {}", chunk)
      chunks.onWriteSuccess(chunk, storageId)

    case ChunkWriteFailed(storageId, chunk, error) ⇒
      log.error(error, "Chunk write failed: {}", chunk)
      chunks.onWriteFailure(chunk, storageId, error)
      // chunks.retryPendingChunks()

    // -----------------------------------------------------------------------
    // Storage events
    // -----------------------------------------------------------------------
    case AttachStorage(storageId, props, dispatcher, health) ⇒
      if (storages.contains(storageId) && storages.getDispatcher(storageId) == dispatcher) {
        // Ignore
      } else {
        if (storages.contains(storageId)) {
          val oldDispatcher = storages.getDispatcher(storageId)
          log.warning("Replacing storage {} with new dispatcher: {} -> {}", storageId, oldDispatcher, dispatcher)
          dropStorageDiffs(storageId)
          storages.unregister(oldDispatcher)
          chunks.unregister(oldDispatcher)
        }

        log.info("Registered storage {}: {}", storageId, dispatcher)
        storages.register(storageId, props, dispatcher, health)
        self ! PullStorageIndex(storageId)
      }

    case DetachStorage(storageId) if storages.contains(storageId) ⇒
      val dispatcher = storages.getDispatcher(storageId)
      dropStorageDiffs(storageId)
      storages.unregister(dispatcher)
      chunks.unregister(dispatcher)

    case GetStorages ⇒
      sender() ! GetStorages.Success(regionId, storages.storages)

    case PullStorageIndex(storageId) if storages.contains(storageId) ⇒
      gcActor ! RegionGC.Defer(1 minute)
      val scheduler = context.system.scheduler
      val storage = storages.getStorage(storageId)

      storage.dispatcher ! StorageIndex.OpenIndex(regionId)
      storage.dispatcher ! StorageDispatcher.CheckHealth

      val indexFuture = RegionIndex.GetIndex.unwrapFuture(storage.dispatcher ?
        StorageIndex.Envelope(regionId, RegionIndex.GetIndex))

      indexFuture.onComplete {
        case Success(IndexMerger.State(Nil, IndexDiff.empty)) | Failure(_: StorageException.NotFound) ⇒
          val diff = globalIndex.mergedDiff.merge(globalIndex.pending)
          if (diff.nonEmpty) {
            log.info("Mirroring index to storage {}: {}", storageId, diff)
            storage.dispatcher ! StorageIndex.Envelope(regionId, WriteDiff(diff))
          }

        case Success(IndexMerger.State(diffs, pending)) ⇒
          log.debug("Storage {} index fetched: {} ({})", storageId, diffs, pending)
          self ! PushDiffs(storageId, diffs, pending)

        case Failure(error) ⇒
          log.error(error, "Error fetching index from storage: {}", storageId)
          scheduler.scheduleOnce(10 seconds, self, PullStorageIndex(storageId))
      }

    case PushDiffs(storageId, diffs, pending) if storages.contains(storageId) ⇒
      globalIndex.addPending(pending)
      addStorageDiffs(storageId, diffs)

    case StorageEnvelope(storageId, event: StorageEvents.Event) if storages.contains(storageId) ⇒ event match {
      case StorageEvents.IndexLoaded(`regionId`, state) ⇒
        log.info("Storage [{}] index loaded: {} diffs", storageId, state.diffs.length)
        dropStorageDiffs(storageId)
        chunks.unregister(storages.getDispatcher(storageId))
        addStorageDiffs(storageId, state.diffs)
        gcActor ! RegionGC.Defer(30 minutes)

      case StorageEvents.IndexUpdated(`regionId`, sequenceNr, diff, _) ⇒
        log.debug("Storage [{}] index updated: {}", storageId, diff)
        addStorageDiff(storageId, sequenceNr, diff)
        gcActor ! RegionGC.Defer(10 minutes)

      case StorageEvents.PendingIndexUpdated(`regionId`, diff) ⇒
        log.debug("Storage [{}] pending index updated: {}", storageId, diff)
        // globalIndex.addPending(diff)

      case StorageEvents.IndexDeleted(`regionId`, sequenceNrs) ⇒
        log.debug("Diffs deleted from storage [{}]: {}", storageId, sequenceNrs)
        dropStorageDiffs(storageId, sequenceNrs)

      case StorageEvents.ChunkWritten(ChunkPath(`regionId`, _), chunk) ⇒
        log.debug("Chunk written: {}", chunk)
        // chunks.onWriteSuccess(chunk, storageId)
        sc.eventStreams.publishRegionEvent(regionId, RegionEvents.ChunkWritten(storageId, chunk))
        gcActor ! RegionGC.Defer(10 minutes)

      case StorageEvents.HealthUpdated(health) ⇒
        log.debug("Storage [{}] health report: {}", storageId, health)
        val wasOffline = {
          val oldHealth = storages.getStorage(storageId).health
          (!oldHealth.online || oldHealth.canWrite < MemorySize.MB) &&
            (health.online && health.canWrite > MemorySize.MB)
        }
        storages.updateHealth(storageId, health)
        if (wasOffline) chunks.retryPendingChunks()

      case _ ⇒
        // Ignore
    }

    case Terminated(dispatcher) ⇒
      log.debug("Watched actor terminated: {}", dispatcher)
      if (storages.contains(dispatcher)) {
        val storageId = storages.getStorageId(dispatcher)
        dropStorageDiffs(storageId)
        storages.unregister(dispatcher)
      }
      chunks.unregister(dispatcher)

    // -----------------------------------------------------------------------
    // GC commands
    // -----------------------------------------------------------------------
    case m: RegionGC.Message ⇒
      gcActor.forward(m)
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  private[this] def addStorageDiffs(storageId: StorageId, diffs: Seq[(Long, IndexDiff)]): Unit = {
    // dropStorageDiffs(storageId, diffs.map(_._1).toSet)
    val dispatcher = storages.getDispatcher(storageId)
    diffs.foreach { case (sequenceNr, diff) ⇒
      val regionKey = RegionKey(diff.time, storageId, sequenceNr)
      globalIndex.add(regionKey, diff)
      chunks.registerDiff(dispatcher, diff.chunks)
      log.debug("Virtual region [{}] index updated: {} -> {}", regionId, regionKey, diff)
      sc.eventStreams.publishRegionEvent(regionId, RegionEvents.IndexUpdated(regionKey, diff))
    }
  }

  @inline
  private[this] def addStorageDiff(storageId: StorageId, sequenceNr: SequenceNr, diff: IndexDiff) = {
    addStorageDiffs(storageId, Seq((sequenceNr, diff)))
  }

  private[this] def dropStorageDiffs(storageId: StorageId, sequenceNrs: Set[Long]): Unit = {
    val preDel = globalIndex.chunks
    val regionKeys = globalIndex.diffs.keys
      .filter(rk ⇒ rk.storageId == storageId && sequenceNrs.contains(rk.sequenceNr))
      .toSet
    globalIndex.delete(regionKeys)
    val deleted = globalIndex.chunks.diff(preDel).deletedChunks
    val dispatcher = storages.getDispatcher(storageId)
    deleted.foreach(chunks.unregisterChunk(dispatcher, _))
    sc.eventStreams.publishRegionEvent(regionId, RegionEvents.IndexDeleted(regionKeys))
  }

  private[this] def dropStorageDiffs(storageId: StorageId): Unit = {
    globalIndex.delete(globalIndex.diffs.keys.filter(_.storageId == storageId).toSet)
  }

  private[this] def folderIndex: FolderIndex = {
    globalIndex.folders.patch(globalIndex.pending.folders)
  }

  // -----------------------------------------------------------------------
  // Lifecycle
  // -----------------------------------------------------------------------
  override def postStop(): Unit = {
    sc.eventStreams.storage.unsubscribe(self)
    pendingIndexQueue.complete()
    super.postStop()
  }
}
