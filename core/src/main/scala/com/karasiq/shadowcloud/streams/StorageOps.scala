package com.karasiq.shadowcloud.streams

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import com.karasiq.shadowcloud.actors.{ChunkIODispatcher, IndexDispatcher}
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
  def synchronize(storageId: String): Unit = {
    regionSupervisor ! StorageEnvelope(storageId, IndexDispatcher.Synchronize)
  }

  def writeIndex(storageId: String, regionId: String, diff: IndexDiff): Future[IndexDiff] = {
    doAsk(storageId, IndexDispatcher.AddPending, IndexDispatcher.AddPending(regionId, diff))
  }

  def compactIndex(storageId: String, region: String): Unit = {
    regionSupervisor ! StorageEnvelope(storageId, IndexDispatcher.CompactIndex(region))
  }

  def getIndex(storageId: String, regionId: String): Future[IndexMerger.State[Long]] = {
    doAsk(storageId, IndexDispatcher.GetIndex, IndexDispatcher.GetIndex(regionId))
  }

  def getIndexes(storageId: String): Future[Map[String, IndexMerger.State[Long]]] = {
    doAsk(storageId, IndexDispatcher.GetIndexes, IndexDispatcher.GetIndexes)
  }

  // -----------------------------------------------------------------------
  // Chunk IO
  // -----------------------------------------------------------------------
  def writeChunk(storageId: String, path: ChunkPath, chunk: Chunk): Future[Chunk] = {
    doAsk(storageId, ChunkIODispatcher.WriteChunk, ChunkIODispatcher.WriteChunk(path, chunk))
  }

  def readChunk(storageId: String, path: ChunkPath, chunk: Chunk): Future[Chunk] = {
    doAsk(storageId, ChunkIODispatcher.ReadChunk, ChunkIODispatcher.ReadChunk(path, chunk))
  }

  def getChunkKeys(storageId: String): Future[Set[ChunkPath]] = {
    doAsk(storageId, ChunkIODispatcher.GetKeys, ChunkIODispatcher.GetKeys)
  }

  def deleteChunks(storageId: String, paths: Set[ChunkPath]): Future[StorageIOResult] = {
    doAsk(storageId, ChunkIODispatcher.DeleteChunks, ChunkIODispatcher.DeleteChunks(paths))
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  private[this] def doAsk[V](storageId: String, status: MessageStatus[_, V], message: Any): Future[V] = {
    (regionSupervisor ? StorageEnvelope(storageId, message)).flatMap {
      case status.Success(_, value) ⇒
        Future.successful(value)

      case status.Failure(_, error) ⇒
        Future.failed(error)
    }
  }
}
