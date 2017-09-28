package com.karasiq.shadowcloud.storage.gdrive

import scala.concurrent.{ExecutionContext, Future}

import com.karasiq.gdrive.files.{GDrive, GDriveService}
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.utils.CacheMap

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

  private[this] val folderIdCache = CacheMap[Path, FileId]
  private[this] val filesCache = CacheMap[Path, FileList]

  def getOrCreateFolderId(path: Path): Future[FileId] = {
    folderIdCache.get(path)(Future(service.createFolder(path.nodes).id))
  }

  def getFolderId(path: Path): Future[FileId] = {
    folderIdCache.get(path)(Future(service.folder(path.nodes).id))
  }

  def getFileIds(path: Path, cached: Boolean = true): Future[FileIds] = {
    def getActualFiles() = getFolderId(path.parent).map(service.files(_, path.name).toVector)

    filesCache.get(path, cached)(getActualFiles()).map { cachedFiles ⇒
      if (cachedFiles.isEmpty) filesCache.remove(path)
      cachedFiles.map(_.id)
    }
  }

  def isFileExists(path: Path): Future[Boolean] = {
    getFileIds(path, cached = false).map(_.nonEmpty)
  }
}
