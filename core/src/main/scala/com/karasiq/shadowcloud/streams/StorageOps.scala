package com.karasiq.shadowcloud.streams

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import com.karasiq.shadowcloud.actors.{ChunkIODispatcher, RegionIndex, StorageIndex}
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.utils.IndexMerger

object StorageOps {
  def apply(regionSupervisor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout): StorageOps = {
    new StorageOps(regionSupervisor)
  }
}

final class StorageOps(regionSupervisor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout) {
  // -----------------------------------------------------------------------
  // Index
  // -----------------------------------------------------------------------
  def synchronize(storageId: String, regionId: String): Unit = {
    regionSupervisor ! StorageEnvelope(storageId, StorageIndex.Envelope(regionId, RegionIndex.Synchronize))
  }

  def writeIndex(storageId: String, regionId: String, diff: IndexDiff): Future[IndexDiff] = {
    askStorageIndex(storageId, regionId, RegionIndex.WriteDiff, RegionIndex.WriteDiff(diff))
  }

  def compactIndex(storageId: String, regionId: String): Unit = {
    regionSupervisor ! StorageEnvelope(storageId, StorageIndex.Envelope(regionId, RegionIndex.Compact))
  }

  def getIndex(storageId: String, regionId: String): Future[IndexMerger.State[Long]] = {
    askStorageIndex(storageId, regionId, RegionIndex.GetIndex, RegionIndex.GetIndex)
  }

  def getIndexes(storageId: String): Future[Map[String, IndexMerger.State[Long]]] = {
    askStorage(storageId, StorageIndex.GetIndexes, StorageIndex.GetIndexes)
  }

  // -----------------------------------------------------------------------
  // Chunk IO
  // -----------------------------------------------------------------------
  def writeChunk(storageId: String, path: ChunkPath, chunk: Chunk): Future[Chunk] = {
    askStorage(storageId, ChunkIODispatcher.WriteChunk, ChunkIODispatcher.WriteChunk(path, chunk))
  }

  def readChunk(storageId: String, path: ChunkPath, chunk: Chunk): Future[Chunk] = {
    askStorage(storageId, ChunkIODispatcher.ReadChunk, ChunkIODispatcher.ReadChunk(path, chunk))
  }

  def getChunkKeys(storageId: String): Future[Set[ChunkPath]] = {
    askStorage(storageId, ChunkIODispatcher.GetKeys, ChunkIODispatcher.GetKeys)
  }

  def deleteChunks(storageId: String, paths: Set[ChunkPath]): Future[StorageIOResult] = {
    askStorage(storageId, ChunkIODispatcher.DeleteChunks, ChunkIODispatcher.DeleteChunks(paths))
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  private[this] def askStorage[V](storageId: String, status: MessageStatus[_, V], message: Any): Future[V] = {
    (regionSupervisor ? StorageEnvelope(storageId, message)).flatMap {
      case status.Success(_, value) ⇒
        Future.successful(value)

      case status.Failure(_, error) ⇒
        Future.failed(error)
    }
  }

  private[this] def askStorageIndex[V](storageId: String, regionId: String,
                             status: MessageStatus[_, V], message: RegionIndex.Message): Future[V] = {
    (regionSupervisor ? StorageEnvelope(storageId, StorageIndex.Envelope(regionId, message))).flatMap {
      case status.Success(_, value) ⇒
        Future.successful(value)

      case status.Failure(_, error) ⇒
        Future.failed(error)
    }
  }
}
