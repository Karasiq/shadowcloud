package com.karasiq.shadowcloud.actors

import java.util.concurrent.TimeoutException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, NotInfluenceReceiveTimeout, PossiblyHarmful, Props, ReceiveTimeout, Status}
import akka.pattern.{ask, pipe}
import akka.util.{ByteString, Timeout}

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.internal.GarbageCollectUtil
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ChunkPath, DeleteChunks ⇒ SDeleteChunks}
import com.karasiq.shadowcloud.actors.utils.{MessageStatus, RegionGCState, StorageGCState}
import com.karasiq.shadowcloud.actors.RegionIndex.WriteDiff
import com.karasiq.shadowcloud.config.{GCConfig, StorageConfig}
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.metadata.MetadataUtils
import com.karasiq.shadowcloud.storage.replication.RegionStorageProvider.RegionStorage
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.utils.{MemorySize, Utils}

object RegionGC {
  // Messages
  sealed trait Message
  case class CollectGarbage(delete: Option[Boolean] = None) extends Message with NotInfluenceReceiveTimeout
  object CollectGarbage extends MessageStatus[String, (RegionGCState, Map[String, StorageGCState])]
  case class Defer(time: FiniteDuration) extends Message with NotInfluenceReceiveTimeout

  private sealed trait InternalMessage extends Message with PossiblyHarmful
  private case class StartGCNow(delete: Option[Boolean] = None) extends InternalMessage
  private case class DeleteGarbage(regionState: RegionGCState, storageStates: Seq[(RegionStorage, StorageGCState)]) extends InternalMessage

  // Props
  private[actors] def props(regionId: String, config: GCConfig): Props = {
    Props(new RegionGC(regionId, config))
  }
}

private[actors] final class RegionGC(regionId: String, config: GCConfig) extends Actor with ActorLogging {
  import context.dispatcher

  import RegionGC._

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] implicit val timeout: Timeout = Timeout(30 seconds)
  private[this] val sc = ShadowCloud()
  private[this] val gcSchedule = context.system.scheduler.schedule(5 minutes, 5 minutes, self, CollectGarbage())(dispatcher, self)
  private[this] var gcDeadline = Deadline.now

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  def receiveIdle: Receive = {
    case CollectGarbage(delete) ⇒
      if (sender() != self || (config.runOnLowSpace.isEmpty && gcDeadline.isOverdue())) {
        log.debug("Starting garbage collection")
        self.tell(StartGCNow(delete), sender())
      } else if (config.runOnLowSpace.nonEmpty && gcDeadline.isOverdue()) {
        val freeSpaceFuture = sc.ops.region.getStorages(regionId).map { storages ⇒
          storages
            .map(_.health.freeSpace)
            .sum
        }

        freeSpaceFuture.onComplete {
          case Success(freeSpace) ⇒
            if (log.isDebugEnabled) log.debug("Estimate free space on region {}: {}", regionId, MemorySize.toString(freeSpace))
            if (config.runOnLowSpace.exists(_ > freeSpace)) {
              if (log.isDebugEnabled) log.debug("Free space lower than {}, starting GC", MemorySize.toString(config.runOnLowSpace.get))
              self ! StartGCNow(delete)
            }

          case Failure(error) ⇒
            log.error(error, "Error getting free space")
        }
      } else {
        // log.debug("Garbage collection will be started in {} minutes", gcDeadline.timeLeft.toMinutes)
      }

    case StartGCNow(delete) ⇒
      context.setReceiveTimeout(10 minutes)
      context.become(receiveCollecting(Set(sender()), delete.getOrElse(config.autoDelete)))
      collectGarbage().map(DeleteGarbage.tupled).pipeTo(self)

    case Defer(time) ⇒
      if (gcDeadline.timeLeft < time) {
        log.debug("GC deferred to {}", time.toCoarsest)
        gcDeadline = time.fromNow
      }
  }

  private[this] def finishCollecting(receivers: Set[ActorRef],
                                     statesFuture: Future[(RegionGCState, Seq[(RegionStorage, StorageGCState)])]): Unit = {
    val statesMapFuture = statesFuture.map { case (regionState, storageStates) ⇒
      (regionState, storageStates.filter(_._2.nonEmpty).map(kv ⇒ kv._1.id → kv._2).toMap)
    }

    CollectGarbage.wrapFuture(regionId, statesMapFuture).foreach { status ⇒
      receivers
        .filter(rv ⇒ rv != self && rv != Actor.noSender)
        .foreach(_ ! status)
    }

    context.setReceiveTimeout(Duration.Undefined)
    context.become(receiveIdle)
    self ! Defer(30 minutes)
  }

  def receiveCollecting(receivers: Set[ActorRef], delete: Boolean): Receive = {
    case CollectGarbage(delete1) ⇒
      val newReceiver = sender()
      val newDelete = delete1.fold(delete)(delete || _)
      context.become(receiveCollecting(receivers + newReceiver, newDelete))

    case DeleteGarbage(regionState, storageStates) ⇒
      val future = deleteGarbage(regionState, storageStates, delete)
        .map(_ ⇒ (regionState, storageStates))
      finishCollecting(receivers, future)

    case Status.Failure(error) ⇒
      finishCollecting(receivers, Future.failed(error))

    case ReceiveTimeout ⇒
      val exception = new TimeoutException("GC timeout")
      finishCollecting(receivers, Future.failed(exception))
      // throw exception
  }

  override def receive: Receive = receiveIdle

  override def postStop(): Unit = {
    gcSchedule.cancel()
    super.postStop()
  }

  // -----------------------------------------------------------------------
  // Garbage deletion
  // -----------------------------------------------------------------------
  private[this] def collectGarbage(): Future[(RegionGCState, Seq[(RegionStorage, StorageGCState)])] = {
    val gcUtil = GarbageCollectUtil(config)

    def createStorageState(index: IndexMerger[_], storage: RegionStorage): Future[(RegionStorage, StorageGCState)] = {
      sc.ops.storage.getChunkKeys(storage.id).map { storageKeys ⇒
        val relevantKeys = storageKeys
          .filter(_.region == regionId)
          .map(_.id)
        (storage, gcUtil.checkStorage(index, storage.config, relevantKeys))
      }
    }

    def createRegionState(index: IndexMerger[_]): RegionGCState = {
      gcUtil.checkRegion(index)
    }

    for {
      regionIndex ← sc.ops.region.getIndex(regionId).map(IndexMerger.restore(RegionKey.zero, _))
      storages ← sc.ops.region.getStorages(regionId)
      regionState = createRegionState(regionIndex)
      storageStates ← Future.sequence(storages.map(createStorageState(regionIndex, _)))
    } yield (regionState, storageStates)
  }

  private[this] def deleteGarbage(regionState: RegionGCState,
                                  storageStates: Seq[(RegionStorage, StorageGCState)],
                                  delete: Boolean): Future[Set[ByteString]] = {
    // Global
    def pushRegionDiff(): Future[IndexDiff] = {
      val folderDiff = {
        val metadataFolders = regionState.expiredMetadata.map(MetadataUtils.getFolderPath)
        FolderIndexDiff.deleteFolderPaths(metadataFolders.toSeq:_*)
          .merge(FolderIndexDiff.deleteFiles(regionState.oldFiles.toSeq:_*))
      }
      sc.ops.region.writeIndex(regionId, folderDiff)
    }

    // Per-storage
    def toDeleteFromStorage(config: StorageConfig, state: StorageGCState): Set[ByteString] = {
      @inline def keys(chunks: Set[Chunk]): Set[ByteString] = chunks.map(config.chunkKey)
      keys(regionState.orphanedChunks) ++ state.notIndexed
    }

    def toDeleteFromIndex(state: StorageGCState): Set[Chunk] = {
      regionState.orphanedChunks ++ state.notExisting
    }

    def deleteFromStorages(chunks: Seq[(RegionStorage, Set[ByteString])]): Future[Set[ByteString]] = {
      val futures = chunks.map { case (storage, chunks) ⇒
        val paths = chunks.map(ChunkPath(regionId, _))
        if (log.isDebugEnabled) log.debug("Deleting chunks from storage {}: [{}]", storage.id, Utils.printHashes(chunks))
        SDeleteChunks.unwrapFuture(storage.dispatcher ? SDeleteChunks(paths))
      }

      Future.foldLeft(futures.toVector)(Set.empty[ByteString])((deleted, result) ⇒
        deleted ++ result.filter(_.region == regionId).map(_.id))
    }

    def deleteFromIndexes(chunks: Seq[(RegionStorage, Set[Chunk])]): Future[Done] = {
      val futures = chunks.map { case (storage, chunks) ⇒
        if (log.isDebugEnabled) log.debug("Deleting chunks from index {}: [{}]", storage.id, Utils.printChunkHashes(chunks))
        WriteDiff.unwrapFuture(storage.dispatcher ?
          StorageIndex.Envelope(regionId, WriteDiff(IndexDiff.deleteChunks(chunks.toSeq: _*))))
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
        _ ← pushRegionDiff()
        deleted ← deleteFromStorages(toDelete)
        _ ← deleteFromIndexes(toUnIndex)
      } yield deleted

      future.onComplete {
        case Success(result) ⇒
          if (log.isInfoEnabled) log.info("Orphaned chunks deleted: [{}]", Utils.printHashes(result))

        case Failure(error) ⇒
          log.error(error, "Error deleting orphaned chunks")
      }

      future
    } else {
      if (regionState.oldFiles.nonEmpty) log.warning("Old files found: [{}]", Utils.printValues(regionState.oldFiles))
      if (hashes.nonEmpty) log.warning("Orphaned chunks found: [{}]", Utils.printHashes(hashes))
      Future.successful(Set.empty)
    }
  }
}
