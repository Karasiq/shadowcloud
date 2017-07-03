package com.karasiq.shadowcloud.streams

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import com.karasiq.shadowcloud.actors.{RegionDispatcher, RegionGC}
import com.karasiq.shadowcloud.actors.RegionDispatcher._
import com.karasiq.shadowcloud.actors.messages.RegionEnvelope
import com.karasiq.shadowcloud.actors.utils.{GCState, MessageStatus}
import com.karasiq.shadowcloud.index.{Chunk, File, Folder, Path}
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.storage.replication.StorageStatusProvider.StorageStatus
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey

object RegionOps {
  def apply(regionSupervisor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout = Timeout(5 minutes)): RegionOps = {
    new RegionOps(regionSupervisor)
  }
}

final class RegionOps(regionSupervisor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout) {
  // -----------------------------------------------------------------------
  // Index
  // -----------------------------------------------------------------------
  def getIndex(regionId: String): Future[IndexMerger.State[RegionKey]] = {
    doAsk(regionId, RegionDispatcher.GetIndex, RegionDispatcher.GetIndex)
  }

  def getFiles(regionId: String, path: Path): Future[Set[File]] = {
    doAsk(regionId, RegionDispatcher.GetFiles, RegionDispatcher.GetFiles(path))
  }

  def getFolder(regionId: String, path: Path): Future[Folder] = {
    doAsk(regionId, RegionDispatcher.GetFolder, RegionDispatcher.GetFolder(path))
  }

  def writeIndex(regionId: String, diff: FolderIndexDiff): Future[IndexDiff] = {
    doAsk(regionId, WriteIndex, WriteIndex(diff))
  }

  def createFolder(regionId: String, path: Path): Future[IndexDiff] = {
    writeIndex(regionId, FolderIndexDiff.create(Folder.create(path)))
  }

  def deleteFiles(regionId: String, files: File*): Future[IndexDiff] = {
    writeIndex(regionId, FolderIndexDiff.deleteFiles(files: _*))
  }

  def deleteFiles(regionId: String, path: Path): Future[Set[File]] = {
    getFiles(regionId, path).flatMap { files ⇒
      writeIndex(regionId, FolderIndexDiff.deleteFiles(files.toSeq: _*)).map(_ ⇒ files)
    }
  }

  def deleteFolder(regionId: String, path: Path): Future[Folder] = {
    getFolder(regionId, path).flatMap { folder ⇒
      writeIndex(regionId, FolderIndexDiff.delete(folder.path)).map(_ ⇒ folder)
    }
  }

  def synchronize(regionId: String): Unit = {
    regionSupervisor ! RegionEnvelope(regionId, RegionDispatcher.Synchronize)
  }

  // -----------------------------------------------------------------------
  // Chunk IO
  // -----------------------------------------------------------------------
  def writeChunk(regionId: String, chunk: Chunk): Future[Chunk] = {
    doAsk(regionId, WriteChunk, WriteChunk(chunk))
  }

  def readChunk(regionId: String, chunk: Chunk): Future[Chunk] = {
    doAsk(regionId, ReadChunk, ReadChunk(chunk))
  }

  // -----------------------------------------------------------------------
  // Storages
  // -----------------------------------------------------------------------
  def getStorages(regionId: String): Future[Seq[StorageStatus]] = {
    doAsk(regionId, RegionDispatcher.GetStorages, RegionDispatcher.GetStorages)
  }

  // -----------------------------------------------------------------------
  // Region GC
  // -----------------------------------------------------------------------
  def collectGarbage(regionId: String, delete: Boolean = false): Future[Map[String, GCState]] = {
    doAsk(regionId, RegionGC.CollectGarbage, RegionGC.CollectGarbage(Some(delete)))
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  private[this] def doAsk[V](regionId: String, status: MessageStatus[_, V], message: Any): Future[V] = {
    (regionSupervisor ? RegionEnvelope(regionId, message)).flatMap {
      case status.Success(_, value) ⇒
        Future.successful(value)

      case status.Failure(_, error) ⇒
        Future.failed(error)
    }
  }
}
