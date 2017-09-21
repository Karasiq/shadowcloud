package com.karasiq.shadowcloud.ops.region

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import com.karasiq.shadowcloud.actors.{RegionDispatcher, RegionGC}
import com.karasiq.shadowcloud.actors.RegionDispatcher._
import com.karasiq.shadowcloud.actors.messages.RegionEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.actors.RegionGC.GCStrategy
import com.karasiq.shadowcloud.config.TimeoutsConfig
import com.karasiq.shadowcloud.index.{ChunkIndex, FolderIndex}
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.index.files.FileVersions
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.utils._
import com.karasiq.shadowcloud.storage.replication.ChunkWriteAffinity
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus
import com.karasiq.shadowcloud.storage.replication.RegionStorageProvider.RegionStorage
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey

object RegionOps {
  def apply(regionSupervisor: ActorRef, timeouts: TimeoutsConfig)(implicit ec: ExecutionContext): RegionOps = {
    new RegionOps(regionSupervisor, timeouts)
  }
}

final class RegionOps(regionSupervisor: ActorRef, timeouts: TimeoutsConfig)(implicit ec: ExecutionContext) {
  // -----------------------------------------------------------------------
  // Index
  // -----------------------------------------------------------------------
  def getChunkIndex(regionId: RegionId, scope: IndexScope = IndexScope.default): Future[ChunkIndex] = {
    askRegion(regionId, RegionDispatcher.GetChunkIndex, RegionDispatcher.GetChunkIndex(scope))
  }

  def getFolderIndex(regionId: RegionId, scope: IndexScope = IndexScope.default): Future[FolderIndex] = {
    askRegion(regionId, RegionDispatcher.GetFolderIndex, RegionDispatcher.GetFolderIndex(scope))
  }

  def getIndexSnapshot(regionId: RegionId, scope: IndexScope = IndexScope.default): Future[IndexMerger.State[RegionKey]] = {
    askRegion(regionId, RegionDispatcher.GetIndexSnapshot, RegionDispatcher.GetIndexSnapshot(scope))
  }

  def getFiles(regionId: RegionId, path: Path, scope: IndexScope = IndexScope.default): Future[Set[File]] = {
    askRegion(regionId, RegionDispatcher.GetFiles, RegionDispatcher.GetFiles(path, scope))
  }

  def getFolder(regionId: RegionId, path: Path, scope: IndexScope = IndexScope.default): Future[Folder] = {
    askRegion(regionId, RegionDispatcher.GetFolder, RegionDispatcher.GetFolder(path, scope))
  }

  def getFileAvailability(regionId: RegionId, file: File): Future[FileAvailability] = {
    askRegion(regionId, RegionDispatcher.GetFileAvailability, RegionDispatcher.GetFileAvailability(file))
  }

  def writeIndex(regionId: RegionId, diff: FolderIndexDiff): Future[IndexDiff] = {
    askRegion(regionId, WriteIndex, WriteIndex(diff))
  }

  def createFolder(regionId: RegionId, path: Path): Future[IndexDiff] = {
    writeIndex(regionId, FolderIndexDiff.createFolders(Folder.create(path)))
  }

  def deleteFiles(regionId: RegionId, files: File*): Future[IndexDiff] = {
    writeIndex(regionId, FolderIndexDiff.deleteFiles(files: _*))
  }

  def deleteFiles(regionId: RegionId, path: Path): Future[Set[File]] = {
    getFiles(regionId, path).flatMap { files ⇒
      writeIndex(regionId, FolderIndexDiff.deleteFiles(files.toSeq: _*)).map(_ ⇒ files)
    }
  }

  def deleteFolder(regionId: RegionId, path: Path): Future[Folder] = {
    getFolder(regionId, path).flatMap { folder ⇒
      writeIndex(regionId, FolderIndexDiff.deleteFolderPaths(folder.path)).map(_ ⇒ folder)
    }
  }

  def synchronize(regionId: RegionId): Future[Map[StorageId, SyncReport]] = {
    askRegion(regionId, RegionDispatcher.Synchronize, RegionDispatcher.Synchronize)
  }

  def compactIndex(regionId: RegionId): Unit = {
    regionSupervisor ! RegionEnvelope(regionId, RegionDispatcher.CompactIndex)
  }

  def getChunkStatus(regionId: RegionId, chunk: Chunk): Future[ChunkStatus] = {
    askRegion(regionId, GetChunkStatus, GetChunkStatus(chunk))
  }

  def createFile(regionId: RegionId, newFile: File): Future[File] = {
    val filesWithSamePath = getFiles(regionId, newFile.path)
      .recover { case _ ⇒ Set.empty[File] }

    filesWithSamePath.flatMap { files ⇒
      val newOrModifiedFile: File = if (files.nonEmpty) {
        val lastRevision = FileVersions.mostRecent(files)
        if (File.isBinaryEquals(lastRevision, newFile)) {
          // Not modified
          lastRevision
        } else {
          // Modified
          File.modified(lastRevision, newFile.checksum, newFile.chunks)
        }
      } else {
        // New file
        newFile
      }

      if (!files.contains(newOrModifiedFile)) {
        val future = askRegion(regionId, WriteIndex, RegionDispatcher.WriteIndex(FolderIndexDiff.createFiles(newOrModifiedFile)))
        future.map(_ ⇒ newOrModifiedFile)
      } else {
        Future.successful(newOrModifiedFile)
      }
    }
  }

  // -----------------------------------------------------------------------
  // Chunk IO
  // -----------------------------------------------------------------------
  def writeChunk(regionId: RegionId, chunk: Chunk): Future[Chunk] = {
    askRegion(regionId, WriteChunk, WriteChunk(chunk))(timeouts.regionChunkWrite)
  }

  def readChunk(regionId: RegionId, chunk: Chunk): Future[Chunk] = {
    askRegion(regionId, ReadChunk, ReadChunk(chunk))(timeouts.regionChunkRead)
  }

  def rewriteChunk(regionId: RegionId, chunk: Chunk, newAffinity: Option[ChunkWriteAffinity]): Future[Chunk] = {
    askRegion(regionId, WriteChunk, RewriteChunk(chunk, newAffinity))
  }

  // -----------------------------------------------------------------------
  // Storages
  // -----------------------------------------------------------------------
  def getStorages(regionId: RegionId): Future[Seq[RegionStorage]] = {
    askRegion(regionId, RegionDispatcher.GetStorages, RegionDispatcher.GetStorages)
  }

  def getHealth(regionId: RegionId): Future[RegionHealth] = {
    askRegion(regionId, RegionDispatcher.GetHealth, RegionDispatcher.GetHealth)
  }

  // -----------------------------------------------------------------------
  // Region GC
  // -----------------------------------------------------------------------
  def collectGarbage(regionId: RegionId, gcStrategy: GCStrategy = GCStrategy.Default): Future[GCReport] = {
    askRegion(regionId, RegionGC.CollectGarbage, RegionGC.CollectGarbage(gcStrategy))
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  private[this] def askRegion[V](regionId: RegionId, status: MessageStatus[_, V], message: Any)
                                (implicit timeout: Timeout = timeouts.query): Future[V] = {
    status.unwrapFuture(regionSupervisor ? RegionEnvelope(regionId, message))
  }
}
