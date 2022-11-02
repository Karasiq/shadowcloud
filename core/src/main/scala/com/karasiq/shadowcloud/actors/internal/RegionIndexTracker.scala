package com.karasiq.shadowcloud.actors.internal

import scala.collection.mutable
import scala.concurrent.Future

import akka.event.Logging
import akka.pattern.ask

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.actors.{RegionIndex, StorageIndex}
import com.karasiq.shadowcloud.actors.events.RegionEvents
import com.karasiq.shadowcloud.actors.RegionIndex.WriteDiff
import com.karasiq.shadowcloud.index.{ChunkIndex, FolderIndex}
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.utils.{FileAvailability, IndexScope, SyncReport}
import com.karasiq.shadowcloud.storage.replication.StorageSelector
import com.karasiq.shadowcloud.storage.replication.RegionStorageProvider.RegionStorage
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.utils.Utils

private[actors] object RegionIndexTracker {
  def apply(regionId: RegionId, chunksTracker: ChunksTracker)(implicit sc: ShadowCloudExtension): RegionIndexTracker = {
    new RegionIndexTracker(regionId, chunksTracker)
  }
}

private[actors] final class RegionIndexTracker(regionId: RegionId, chunksTracker: ChunksTracker)(implicit sc: ShadowCloudExtension) {
  import sc.implicits.{defaultTimeout, executionContext}

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] val log = Logging(sc.implicits.actorSystem, s"$regionId-index")
  val globalIndex       = IndexMerger.region()

  // -----------------------------------------------------------------------
  // Storages
  // -----------------------------------------------------------------------
  object storages {
    object state {
      def extractDiffs(storageId: StorageId): Seq[(SequenceNr, IndexDiff)] = {
        val diffs =
          for ((RegionKey(_, `storageId`, sequenceNr), diff) ← globalIndex.diffs)
            yield (sequenceNr, diff)
        diffs.toVector
      }

      def extractIndex(storageId: StorageId): IndexMerger[SequenceNr] = {
        IndexMerger.restore(IndexMerger.State(extractDiffs(storageId)))
      }

      def addStorageDiffs(storageId: StorageId, diffs: Seq[(SequenceNr, IndexDiff)]): Unit = {
        // dropStorageDiffs(storageId, diffs.map(_._1).toSet)
        diffs.foreach { case (sequenceNr, diff) ⇒
          val regionKey = RegionKey(diff.time, storageId, sequenceNr)
          globalIndex.add(regionKey, diff)
          chunksTracker.storages.state.registerDiff(storageId, diff.chunks)
          log.debug("Virtual region [{}] index updated: {} -> {}", regionId, regionKey, diff)
          sc.eventStreams.publishRegionEvent(regionId, RegionEvents.IndexUpdated(regionKey, diff))
        }
      }

      def addStorageDiff(storageId: StorageId, sequenceNr: SequenceNr, diff: IndexDiff) = {
        addStorageDiffs(storageId, Seq((sequenceNr, diff)))
      }

      def dropStorageDiffs(storageId: StorageId, sequenceNrs: Set[SequenceNr]): Unit = {
        val preDel = globalIndex.chunks
        val regionKeys = globalIndex.diffs.keys
          .filter(rk ⇒ rk.storageId == storageId && sequenceNrs.contains(rk.sequenceNr))
          .toSet
        globalIndex.delete(regionKeys)
        val deleted = globalIndex.chunks.diff(preDel).deletedChunks
        deleted.foreach(chunksTracker.storages.state.unregisterChunk(storageId, _))
        sc.eventStreams.publishRegionEvent(regionId, RegionEvents.IndexDeleted(regionKeys))
      }

      def dropStorageDiffs(storageId: StorageId): Unit = {
        globalIndex.delete(globalIndex.diffs.keys.filter(_.storageId == storageId).toSet)
      }
    }

    object io {
      def synchronize(storage: RegionStorage): Future[SyncReport] = {
        val future = (storage.dispatcher ? StorageIndex.Envelope(regionId, RegionIndex.Synchronize))(sc.config.timeouts.synchronize)
        RegionIndex.Synchronize.unwrapFuture(future)
      }

      def getIndex(storage: RegionStorage): Future[IndexMerger.State[SequenceNr]] = {
        RegionIndex.GetIndex.unwrapFuture(
          storage.dispatcher ?
            StorageIndex.Envelope(regionId, RegionIndex.GetIndex)
        )
      }

      def writeIndex(storage: RegionStorage, diff: IndexDiff): Unit = {
        log.debug("Writing index to {}: {}", storage.id, diff)
        storage.dispatcher ! StorageIndex.Envelope(regionId, WriteDiff(diff))
      }

      def writeIndex(diff: IndexDiff)(implicit storageSelector: StorageSelector): Seq[RegionStorage] = {
        log.debug("Writing region index diff: {}", diff)
        val storages = storageSelector.forIndexWrite(diff)
        if (storages.isEmpty) {
          log.warning("No index storages available on {}", regionId)
        } else {
          if (log.isDebugEnabled) {
            log.debug("Writing to virtual region [{}] index: {} (storages = [{}])", regionId, diff, Utils.printValues(storages.map(_.id)))
          }

          storages.foreach(_.dispatcher ! StorageIndex.Envelope(regionId, WriteDiff(diff)))
        }
        storages
      }
    }
  }

  // -----------------------------------------------------------------------
  // Local index operations
  // -----------------------------------------------------------------------
  object indexes {
    private[this] val indexScopeCache = mutable.WeakHashMap.empty[IndexScope, IndexMerger[RegionKey]]

    def chunks(scope: IndexScope = IndexScope.default): ChunkIndex = {
      val index = this.withScope(scope)
      index.chunks.patch(index.pending.chunks)
    }

    def folders(scope: IndexScope = IndexScope.default): FolderIndex = {
      val index = this.withScope(scope)
      index.folders.patch(index.pending.folders)
    }

    def withScope(scope: IndexScope): IndexMerger[RegionKey] = scope match {
      case IndexScope.UntilSequenceNr(_) | IndexScope.UntilTime(_) ⇒
        getOrCreateScopedIndex(scope)

      case _ ⇒
        createScopedIndex(scope)
    }

    def getState(scope: IndexScope): IndexMerger.State[RegionKey] = {
      IndexMerger.createState(this.withScope(scope))
    }

    def pending: IndexDiff = {
      globalIndex.pending
    }

    def markAsPending(diff: IndexDiff): Unit = {
      globalIndex.addPending(diff)
    }

    def registerChunk(chunk: Chunk): Unit = {
      globalIndex.addPending(IndexDiff.newChunks(chunk))
    }

    def toMergedDiff: IndexDiff = {
      globalIndex.mergedDiff.merge(globalIndex.pending)
    }

    def getFileAvailability(file: File): FileAvailability = {
      val chunkStoragePairs = file.chunks.flatMap { chunk ⇒
        chunksTracker.chunks
          .getChunkStatus(chunk)
          .toSeq
          .flatMap(_.availability.hasChunk)
          .map(_ → chunk)
      }

      val chunksByStorage = chunkStoragePairs
        .groupBy(_._1)
        .mapValues(_.map(_._2).toSet)

      FileAvailability(file, chunksByStorage)
    }

    private[this] def createScopedIndex(scope: IndexScope): IndexMerger[RegionKey] = {
      IndexMerger.scopedView(globalIndex, scope)
    }

    private[this] def getOrCreateScopedIndex(scope: IndexScope): IndexMerger[RegionKey] = {
      indexScopeCache.getOrElseUpdate(scope, createScopedIndex(scope))
    }
  }
}
