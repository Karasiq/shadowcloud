package com.karasiq.shadowcloud.streams

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.{higherKinds, postfixOps}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import com.karasiq.shadowcloud.actors.RegionDispatcher
import com.karasiq.shadowcloud.actors.RegionDispatcher._
import com.karasiq.shadowcloud.actors.messages.RegionEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.index.{Chunk, File, Folder, Path}
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}

object RegionOps {
  def apply(regionSupervisor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout = Timeout(5 minutes)): RegionOps = {
    new RegionOps(regionSupervisor)
  }
}

final class RegionOps(regionSupervisor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout) {
  def writeChunk(regionId: String, chunk: Chunk): Future[Chunk] = {
    doAsk(regionId, WriteChunk, WriteChunk(chunk))
  }

  def readChunk(regionId: String, chunk: Chunk): Future[Chunk] = {
    doAsk(regionId, ReadChunk, ReadChunk(chunk))
  }

  def getFiles(regionId: String, path: Path): Future[Set[File]] = {
    doAsk(regionId, GetFiles, GetFiles(path))
  }

  def getFolder(regionId: String, path: Path): Future[Folder] = {
    doAsk(regionId, GetFolder, GetFolder(path))
  }

  def createFolder(regionId: String, path: Path): Future[IndexDiff] = {
    writeIndex(regionId, FolderIndexDiff.create(Folder.create(path)))
  }

  def deleteFiles(regionId: String, files: File*): Future[IndexDiff] = {
    writeIndex(regionId, FolderIndexDiff.deleteFiles(files:_*))
  }

  def deleteFiles(regionId: String, path: Path): Future[Set[File]] = {
    getFiles(regionId, path).flatMap { files ⇒
      writeIndex(regionId, FolderIndexDiff.deleteFiles(files.toSeq:_*)).map(_ ⇒ files)
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

  private[this] def writeIndex(regionId: String, diff: FolderIndexDiff): Future[IndexDiff] = {
    doAsk(regionId, WriteIndex, WriteIndex(diff))
  }

  private[this] def doAsk[V](regionId: String, status: MessageStatus[_, V], message: Any): Future[V] = {
    (regionSupervisor ? RegionEnvelope(regionId, message)).flatMap {
      case status.Success(_, value) ⇒
        Future.successful(value)

      case status.Failure(_, error) ⇒
        Future.failed(error)
    }
  }
}
