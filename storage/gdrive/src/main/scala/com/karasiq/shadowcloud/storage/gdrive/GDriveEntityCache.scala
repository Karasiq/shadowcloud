package com.karasiq.shadowcloud.storage.gdrive

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

import com.karasiq.gdrive.files.{GDrive, GDriveService}
import com.karasiq.shadowcloud.model.Path

private[gdrive] object GDriveEntityCache {
  def apply(service: GDriveService)(implicit ec: ExecutionContext): GDriveEntityCache = {
    new GDriveEntityCache(service)
  }
}

// TODO: LRU cache
private[gdrive] final class GDriveEntityCache(service: GDriveService)(implicit ec: ExecutionContext) {
  type FileList = Vector[GDrive.Entity]
  type FileId = String
  type FileIds = Vector[FileId]

  private[this] val folderIdCache = TrieMap.empty[Path, Future[FileId]]
  private[this] val filesCache = TrieMap.empty[Path, Future[FileList]]

  def getOrCreateFolderId(path: Path): Future[FileId] = {
    folderIdCache.getOrElseUpdate(path, Future(service.createFolder(path.nodes).id))
  }

  def getFolderId(path: Path): Future[FileId] = {
    folderIdCache.getOrElseUpdate(path, Future(service.folder(path.nodes).id))
  }

  def getFileIds(path: Path, cached: Boolean = true): Future[FileIds] = {
    def getActualFiles() = getFolderId(path.parent).map(service.files(_, path.name).toVector)

    val cachedFiles = filesCache.getOrElseUpdate(path, getActualFiles())
    cachedFiles.flatMap { cachedFiles ⇒
      if (cached && cachedFiles.nonEmpty) {
        Future.successful(cachedFiles.map(_.id))
      } else {
        val future = getActualFiles()
        filesCache += path → future
        future.map(_.map(_.id))
      }
    }
  }

  def isFileExists(path: Path): Future[Boolean] = {
    getFileIds(path, cached = false).map(_.nonEmpty)
  }
}
