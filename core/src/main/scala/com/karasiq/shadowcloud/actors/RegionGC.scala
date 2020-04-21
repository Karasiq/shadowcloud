package com.karasiq.shadowcloud.actors

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, NotInfluenceReceiveTimeout, PossiblyHarmful, Props, ReceiveTimeout, Status}
import akka.pattern.{ask, pipe}
import akka.util.{ByteString, Timeout}
import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ChunkPath, DeleteChunks => SDeleteChunks}
import com.karasiq.shadowcloud.actors.RegionIndex.WriteDiff
import com.karasiq.shadowcloud.actors.internal.GarbageCollectUtil
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.{RegionConfig, StorageConfig}
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.metadata.MetadataUtils
import com.karasiq.shadowcloud.model.utils.GCReport
import com.karasiq.shadowcloud.model.utils.GCReport.{RegionGCState, StorageGCState}
import com.karasiq.shadowcloud.model.{Chunk, RegionId}
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.replication.RegionStorageProvider.RegionStorage
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.storage.utils.{IndexMerger, StorageUtils}
import com.karasiq.shadowcloud.utils.{ChunkUtils, Utils}

import scala.concurrent.duration._
import scala.concurrent.{Future, TimeoutException}
import scala.util.{Failure, Success}

object RegionGC {
  // Types
  sealed trait GCStrategy
  object GCStrategy {
    case object Default extends GCStrategy
    case object Preview extends GCStrategy
    case object Delete  extends GCStrategy
  }

  // Messages
  sealed trait Message
  final case class CollectGarbage(strategy: GCStrategy = GCStrategy.Default) extends Message with NotInfluenceReceiveTimeout
  object CollectGarbage                                                      extends MessageStatus[RegionId, GCReport]
  final case class Defer(time: FiniteDuration)                               extends Message with NotInfluenceReceiveTimeout
  final case class Reserve(chunks: Set[Chunk])                               extends Message with NotInfluenceReceiveTimeout
  final case class UnReserve(chunks: Set[Chunk])                             extends Message with NotInfluenceReceiveTimeout

  private sealed trait InternalMessage                                                                                    extends Message with PossiblyHarmful
  private final case class StartGCNow(strategy: GCStrategy = GCStrategy.Default)                                          extends InternalMessage
  private final case class DeleteGarbage(regionState: RegionGCState, storageStates: Seq[(RegionStorage, StorageGCState)]) extends InternalMessage

  // Props
  private[actors] def props(regionId: RegionId, config: RegionConfig): Props = {
    Props(new RegionGC(regionId, config))
  }
}

private[actors] final class RegionGC(regionId: RegionId, regionConfig: RegionConfig) extends Actor with ActorLogging {
  import RegionGC._
  import context.dispatcher

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] implicit val timeout: Timeout            = Timeout(30 seconds)
  private[this] implicit val sc                          = ShadowCloud()
  private[this] val gcConfig                             = regionConfig.garbageCollector
  private[this] val gcSchedule                           = context.system.scheduler.schedule(5 minutes, 5 minutes, self, CollectGarbage())(dispatcher, self)
  private[this] var gcDeadline                           = Deadline.now
  private[this] var timeoutSchedule: Option[Cancellable] = None
  private[this] var reserved                             = Set.empty[Chunk]

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  def receiveIdle: Receive = {
    case CollectGarbage(gcStrategy) ⇒
      if (sender() != self || (gcConfig.autoDelete && gcConfig.runOnLowSpace.isEmpty && gcDeadline.isOverdue())) {
        log.info("Starting garbage collection on {}", regionId)
        self.tell(StartGCNow(gcStrategy), sender())
      } else if (gcConfig.autoDelete && gcConfig.runOnLowSpace.nonEmpty && gcDeadline.isOverdue()) {
        val healthFuture = sc.ops.region.getHealth(regionId)
        healthFuture.onComplete {
          case Success(health) ⇒
            log.debug("Estimate free space on region {}: {}", regionId, MemorySize(health.freeSpace))
            if (health.fullyOnline && gcConfig.runOnLowSpace.exists(_ > health.freeSpace)) {
              log.debug("Free space lower than {}, starting GC", MemorySize(gcConfig.runOnLowSpace.get))
              self ! StartGCNow(gcStrategy)
            }

          case Failure(error) ⇒
            log.error(error, "Error getting free space")
        }
      } else {
        // log.debug("Garbage collection will be started in {} minutes", gcDeadline.timeLeft.toMinutes)
      }

    case StartGCNow(gcStrategy) ⇒
      context.setReceiveTimeout(10 minutes)
      timeoutSchedule.foreach(_.cancel())
      timeoutSchedule = Some(context.system.scheduler.scheduleOnce(10 minute, self, ReceiveTimeout))
      context.become(receiveCollecting(Set(sender()), gcStrategy))

      val result = sc.ops.region.synchronize(regionId).flatMap { reports =>
        if (reports.exists(_._2.nonEmpty)) log.warning("Pre-GC sync reports: {}", reports)
        collectGarbage().map(DeleteGarbage.tupled)
      }
      result.pipeTo(self)

    case Defer(time) ⇒
      if (gcDeadline.timeLeft < time) {
        log.debug("GC deferred to {}", time.toCoarsest)
        gcDeadline = time.fromNow
      }
  }

  def receiveReserve: Receive = {
    case Reserve(chunks)   => reserved ++= chunks.map(_.withoutData)
    case UnReserve(chunks) => reserved --= chunks
  }

  private[this] def finishCollecting(receivers: Set[ActorRef], statesFuture: Future[(RegionGCState, Seq[(RegionStorage, StorageGCState)])]): Unit = {
    val futureGcResult = statesFuture.map {
      case (regionState, storageStates) ⇒
        GCReport(regionId, regionState, storageStates.filter(_._2.nonEmpty).map(kv ⇒ kv._1.id → kv._2).toMap)
    }

    CollectGarbage.wrapFuture(regionId, futureGcResult).foreach { status ⇒
      receivers
        .filter(rv ⇒ rv != self && rv != Actor.noSender)
        .foreach(_ ! status)
    }

    timeoutSchedule.foreach(_.cancel())
    timeoutSchedule = None
    context.become(receiveIdle.orElse(receiveReserve))
    self ! Defer(30 minutes)
  }

  def receiveCollecting(receivers: Set[ActorRef], gcStrategy: GCStrategy): Receive = {
    case CollectGarbage(strategy1) ⇒
      val newReceiver = sender()
      val newStrategy = strategy1 match {
        case GCStrategy.Default | GCStrategy.Preview ⇒
          gcStrategy

        case GCStrategy.Delete ⇒
          GCStrategy.Delete
      }
      context.become(receiveCollecting(receivers + newReceiver, newStrategy).orElse(receiveReserve))

    case DeleteGarbage(regionState, storageStates) ⇒
      log.warning("Region GC states: {}, {}", regionState, storageStates)
      val delete = gcStrategy match {
        case GCStrategy.Default ⇒ gcConfig.autoDelete
        case GCStrategy.Preview ⇒ false
        case GCStrategy.Delete  ⇒ true
      }
      val patchedRegionState = regionState.copy(orphanedChunks = regionState.orphanedChunks -- reserved)
      val patchedStorageStates = storageStates.map {
        case (st, ss) =>
          val chunkKey = ChunkUtils.getChunkKeyMapper(regionConfig, st.config)
          st -> ss.copy(ss.notIndexed -- reserved.map(chunkKey), ss.notExisting -- reserved)
      }
      val future = deleteGarbage(patchedRegionState, patchedStorageStates, delete)
        .map(_ ⇒ (patchedRegionState, patchedStorageStates))
      finishCollecting(receivers, future)

    case Status.Failure(error) ⇒
      log.error(error, "GC failure")
      finishCollecting(receivers, Future.failed(error))

    case ReceiveTimeout ⇒
      finishCollecting(receivers, Future.failed(new TimeoutException("GC timeout")))
  }

  override def receive: Receive = receiveIdle.orElse(receiveReserve)

  override def postStop(): Unit = {
    gcSchedule.cancel()
    super.postStop()
  }

  // -----------------------------------------------------------------------
  // Garbage deletion
  // -----------------------------------------------------------------------
  private[this] def collectGarbage(): Future[(RegionGCState, Seq[(RegionStorage, StorageGCState)])] = {
    val gcUtil = GarbageCollectUtil(regionConfig)

    def createStorageState(regionIndex: IndexMerger[RegionKey], storage: RegionStorage): Future[(RegionStorage, StorageGCState)] = {
      val subIndex = {
        val relevantDiffs = regionIndex.diffs.filterKeys(_.storageId == storage.id)
        IndexMerger.restore(IndexMerger.State(relevantDiffs.toSeq, regionIndex.pending))
      }

      sc.ops.storage.getChunkKeys(storage.id, regionId).map { chunkIds ⇒
        (storage, gcUtil.checkStorage(subIndex, storage.config, chunkIds))
      }
    }

    def createRegionState(index: IndexMerger[RegionKey]): RegionGCState = {
      gcUtil.checkRegion(index)
    }

    for {
      regionIndex ← sc.ops.region.getIndexSnapshot(regionId).map(IndexMerger.restore(_))
      storages    ← sc.ops.region.getStorages(regionId)
      regionState = createRegionState(regionIndex)
      storageStates ← Future.sequence(storages.map(createStorageState(regionIndex, _)))
    } yield (regionState, storageStates)
  }

  private[this] def deleteGarbage(
      regionState: RegionGCState,
      storageStates: Seq[(RegionStorage, StorageGCState)],
      delete: Boolean
  ): Future[(Set[ByteString], StorageIOResult)] = {
    // Global
    def pushRegionDiff(): Future[FolderIndexDiff] = {
      val folderDiff = {
        val metadataFolders = regionState.expiredMetadata.map(MetadataUtils.getFolderPath)
        FolderIndexDiff
          .deleteFolderPaths(metadataFolders.toSeq: _*)
          .merge(FolderIndexDiff.deleteFiles(regionState.oldFiles.toSeq: _*))
      }

      if (folderDiff.nonEmpty) {
        sc.ops.region.writeIndex(regionId, folderDiff).map(_ ⇒ folderDiff)
      } else {
        Future.successful(folderDiff)
      }
    }

    // Per-storage
    def toDeleteFromStorage(config: StorageConfig, state: StorageGCState): Set[ByteString] = {
      val chunkKey                                                 = ChunkUtils.getChunkKeyMapper(regionConfig, config)
      @inline def extractKeys(chunks: Set[Chunk]): Set[ByteString] = chunks.map(chunkKey)
      extractKeys(regionState.orphanedChunks) ++ state.notIndexed
    }

    def toDeleteFromIndex(state: StorageGCState): Set[Chunk] = {
      regionState.orphanedChunks ++ state.notExisting
    }

    def deleteFromStorages(chunks: Seq[(RegionStorage, Set[ByteString])]): Future[(Set[ByteString], StorageIOResult)] = {
      val futures = chunks.map {
        case (storage, chunks) ⇒
          val paths = chunks.map(ChunkPath(regionId, _))
          if (log.isDebugEnabled) log.debug("Deleting chunks from storage {}: [{}]", storage.id, Utils.printHashes(chunks))
          SDeleteChunks.unwrapFuture((storage.dispatcher ? SDeleteChunks(paths))(sc.config.timeouts.chunksDelete))
      }

      Future.foldLeft(futures.toVector)((Set.empty[ByteString], StorageIOResult.empty)) {
        case ((deleted, result), (deleted1, result1)) ⇒
          (deleted ++ deleted1.map(_.chunkId), StorageUtils.foldIOResultsIgnoreErrors(result, result1))
      }
    }

    def deleteFromIndexes(chunks: Seq[(RegionStorage, Set[Chunk])]): Future[Done] = {
      val futures = chunks.map {
        case (storage, chunks) ⇒
          if (log.isDebugEnabled) log.debug("Deleting chunks from index {}: [{}]", storage.id, Utils.printChunkHashes(chunks))
          WriteDiff.unwrapFuture(
            storage.dispatcher ?
              StorageIndex.Envelope(regionId, WriteDiff(IndexDiff.deleteChunks(chunks.toSeq: _*)))
          )
      }
      Future.foldLeft(futures.toList)(Done)((_, _) ⇒ Done)
    }

    val toDelete = for {
      (storage, state) ← storageStates
      keys = toDeleteFromStorage(storage.config, state) if keys.nonEmpty
    } yield (storage, keys)

    val toUnIndex = for {
      (storage, state) ← storageStates
      chunks = toDeleteFromIndex(state) if chunks.nonEmpty
    } yield (storage, chunks)

    val hashes: Set[ByteString] = if (log.isWarningEnabled) {
      (toDelete.flatMap(_._2) ++ toUnIndex.flatMap(_._2).map(_.checksum.hash)).toSet
    } else {
      Set.empty
    }

    if (delete) {
      log.warning("Deleting orphaned chunks: [{}]", Utils.printHashes(hashes))

      val future = for {
        _       ← pushRegionDiff()
        deleted ← deleteFromStorages(toDelete)
        _       ← deleteFromIndexes(toUnIndex)
      } yield deleted

      future.onComplete {
        case Success((hashes, ioResult)) ⇒
          if (log.isInfoEnabled) log.info("Orphaned chunks deleted: [{}] ({})", Utils.printHashes(hashes), MemorySize(ioResult.count))

        case Failure(error) ⇒
          log.error(error, "Error deleting orphaned chunks")
      }

      future
    } else {
      if (regionState.oldFiles.nonEmpty) log.warning("Old files found: [{}]", Utils.printValues(regionState.oldFiles))
      if (hashes.nonEmpty) log.warning("Orphaned chunks found: [{}]", Utils.printHashes(hashes))
      Future.successful((Set.empty, StorageIOResult.empty))
    }
  }
}
