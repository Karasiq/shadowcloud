package com.karasiq.shadowcloud.streams

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import com.karasiq.shadowcloud.actors.{RegionDispatcher, RegionGC}
import com.karasiq.shadowcloud.actors.RegionDispatcher._
import com.karasiq.shadowcloud.actors.messages.RegionEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.actors.RegionGC.GCReport
import com.karasiq.shadowcloud.config.TimeoutsConfig
import com.karasiq.shadowcloud.index.{Chunk, File, Folder, Path}
import com.karasiq.shadowcloud.index.diffs.{FileVersions, FolderIndexDiff, IndexDiff}
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
  def getIndex(regionId: String): Future[IndexMerger.State[RegionKey]] = {
    askRegion(regionId, RegionDispatcher.GetIndex, RegionDispatcher.GetIndex)
  }

  def getFiles(regionId: String, path: Path): Future[Set[File]] = {
    askRegion(regionId, RegionDispatcher.GetFiles, RegionDispatcher.GetFiles(path))
  }

  def getFolder(regionId: String, path: Path): Future[Folder] = {
    askRegion(regionId, RegionDispatcher.GetFolder, RegionDispatcher.GetFolder(path))
  }

  def writeIndex(regionId: String, diff: FolderIndexDiff): Future[IndexDiff] = {
    askRegion(regionId, WriteIndex, WriteIndex(diff))
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
      writeIndex(regionId, FolderIndexDiff.deleteFolderPaths(folder.path)).map(_ ⇒ folder)
    }
  }

  def synchronize(regionId: String): Unit = {
    regionSupervisor ! RegionEnvelope(regionId, RegionDispatcher.Synchronize)
  }

  def getChunkStatus(regionId: String, chunk: Chunk): Future[ChunkStatus] = {
    askRegion(regionId, GetChunkStatus, GetChunkStatus(chunk))
  }

  def createFile(regionId: String, newFile: File): Future[File] = {
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
  def writeChunk(regionId: String, chunk: Chunk): Future[Chunk] = {
    askRegion(regionId, WriteChunk, WriteChunk(chunk))(timeouts.regionChunkWrite)
  }

  def readChunk(regionId: String, chunk: Chunk): Future[Chunk] = {
    askRegion(regionId, ReadChunk, ReadChunk(chunk))(timeouts.regionChunkRead)
  }

  def rewriteChunk(regionId: String, chunk: Chunk, newAffinity: Option[ChunkWriteAffinity]): Future[Chunk] = {
    askRegion(regionId, WriteChunk, RewriteChunk(chunk, newAffinity))
  }

  // -----------------------------------------------------------------------
  // Storages
  // -----------------------------------------------------------------------
  def getStorages(regionId: String): Future[Seq[RegionStorage]] = {
    askRegion(regionId, RegionDispatcher.GetStorages, RegionDispatcher.GetStorages)
  }

  // -----------------------------------------------------------------------
  // Region GC
  // -----------------------------------------------------------------------
  def collectGarbage(regionId: String, delete: Boolean = false): Future[GCReport] = {
    askRegion(regionId, RegionGC.CollectGarbage, RegionGC.CollectGarbage(Some(delete)))
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  private[this] def askRegion[V](regionId: String, status: MessageStatus[_, V], message: Any)
                                (implicit timeout: Timeout = timeouts.query): Future[V] = {
    status.unwrapFuture(regionSupervisor ? RegionEnvelope(regionId, message))
  }
}
