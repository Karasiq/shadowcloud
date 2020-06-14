package com.karasiq.shadowcloud.ops.storage

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.actors.{ChunkIODispatcher, RegionIndex, StorageDispatcher, StorageIndex}
import com.karasiq.shadowcloud.config.TimeoutsConfig
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.model.utils.{StorageHealth, SyncReport}
import com.karasiq.shadowcloud.model.{Chunk, ChunkId, RegionId, StorageId}
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.utils.IndexMerger

import scala.concurrent.{ExecutionContext, Future}

object StorageOps {
  def apply(regionSupervisor: ActorRef, timeouts: TimeoutsConfig)(implicit ec: ExecutionContext): StorageOps = {
    new StorageOps(regionSupervisor, timeouts)
  }
}

final class StorageOps(regionSupervisor: ActorRef, timeouts: TimeoutsConfig)(implicit ec: ExecutionContext) {
  // -----------------------------------------------------------------------
  // Index
  // -----------------------------------------------------------------------
  def synchronize(storageId: StorageId, regionId: RegionId): Future[SyncReport] = {
    askStorageIndex(storageId, regionId, RegionIndex.Synchronize, RegionIndex.Synchronize)(timeouts.synchronize)
  }

  def synchronizeAll(storageId: StorageId): Future[Map[RegionId, SyncReport]] = {
    askStorage(storageId, StorageIndex.SynchronizeAll, StorageIndex.SynchronizeAll)(timeouts.synchronize)
  }

  def writeIndex(storageId: StorageId, regionId: RegionId, diff: IndexDiff): Future[IndexDiff] = {
    askStorageIndex(storageId, regionId, RegionIndex.WriteDiff, RegionIndex.WriteDiff(diff))
  }

  def compactIndex(storageId: StorageId, regionId: RegionId): Unit = {
    regionSupervisor ! StorageEnvelope(storageId, StorageIndex.Envelope(regionId, RegionIndex.Compact))
  }

  def getIndex(storageId: StorageId, regionId: RegionId): Future[IndexMerger.State[Long]] = {
    askStorageIndex(storageId, regionId, RegionIndex.GetIndex, RegionIndex.GetIndex)
  }

  def getIndexes(storageId: StorageId): Future[Map[String, IndexMerger.State[Long]]] = {
    askStorage(storageId, StorageIndex.GetIndexes, StorageIndex.GetIndexes)
  }

  // -----------------------------------------------------------------------
  // Chunk IO
  // -----------------------------------------------------------------------
  def writeChunk(storageId: StorageId, path: ChunkPath, chunk: Chunk): Future[Chunk] = {
    askStorage(storageId, ChunkIODispatcher.WriteChunk, ChunkIODispatcher.WriteChunk(path, chunk))(timeouts.chunkWrite)
  }

  def readChunk(storageId: StorageId, path: ChunkPath, chunk: Chunk): Future[Chunk] = {
    askStorage(storageId, ChunkIODispatcher.ReadChunk, ChunkIODispatcher.ReadChunk(path, chunk))(timeouts.chunkRead)
  }

  def getChunkKeys(storageId: StorageId, regionId: RegionId): Future[Set[ChunkId]] = {
    askStorage(storageId, ChunkIODispatcher.GetKeys, ChunkIODispatcher.GetKeys(regionId))(timeouts.chunksList)
  }

  def deleteChunks(storageId: StorageId, paths: Set[ChunkPath]): Future[(Set[ChunkPath], StorageIOResult)] = {
    askStorage(storageId, ChunkIODispatcher.DeleteChunks, ChunkIODispatcher.DeleteChunks(paths))(timeouts.chunksDelete)
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  def getHealth(storageId: StorageId, checkNow: Boolean = false): Future[StorageHealth] = {
    implicit val timeout = if (checkNow) timeouts.chunksList else timeouts.query
    askStorage(storageId, StorageDispatcher.GetHealth, StorageDispatcher.GetHealth(checkNow))
  }

  private[this] def askStorage[V](storageId: StorageId, status: MessageStatus[_, V], message: Any)
                                 (implicit timeout: Timeout = timeouts.query): Future[V] = {
    status.unwrapFuture(regionSupervisor ? StorageEnvelope(storageId, message))
  }

  private[this] def askStorageIndex[V](storageId: StorageId, regionId: RegionId,
                                       status: MessageStatus[_, V], message: RegionIndex.Message)
                                      (implicit timeout: Timeout = timeouts.query): Future[V] = {
    askStorage(storageId, status, StorageIndex.Envelope(regionId, message))(timeout)
  }
}
