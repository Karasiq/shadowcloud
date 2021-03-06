package com.karasiq.shadowcloud.storage.gdrive

import com.karasiq.gdrive.files.GDriveService.TeamDriveId
import com.karasiq.gdrive.files.{GDrive, GDriveService}
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.utils.CacheMap

import scala.concurrent.{ExecutionContext, Future}

private[gdrive] object GDriveEntityCache {
  def apply(service: GDriveService)(implicit ec: ExecutionContext, td: TeamDriveId): GDriveEntityCache = {
    new GDriveEntityCache(service)
  }
}

// TODO: LRU cache
private[gdrive] final class GDriveEntityCache(service: GDriveService)(implicit ec: ExecutionContext, td: TeamDriveId) {
  type FileList = Vector[GDrive.Entity]
  type FileId   = String
  type FileIds  = Vector[FileId]

  private[this] val folderIdCache = CacheMap[Path, FileId]
  private[this] val filesCache    = CacheMap[Path, FileList]

  def getOrCreateFolderId(path: Path): Future[FileId] = {
    folderIdCache(path)(Future(service.createFolder(path.nodes).id))
  }

  def getFolderId(path: Path): Future[FileId] = {
    folderIdCache(path)(Future(service.folderAt(path.nodes).id))
  }

  def getFileIds(path: Path, cached: Boolean = true): Future[FileIds] = {
    def getActualFiles() =
      getFolderId(path.parent).map(service.filesInWithName(_, path.name).toVector)

    filesCache(path, cached)(getActualFiles()).map { cachedFiles ⇒
      if (cachedFiles.isEmpty) resetFileCache(path)
      cachedFiles.map(_.id)
    }
  }

  def resetFileCache(path: Path): Unit = {
    filesCache -= path
  }

  def isFileExists(path: Path): Future[Boolean] = {
    getFileIds(path, cached = false).map(_.nonEmpty)
  }
}
