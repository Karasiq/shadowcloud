package com.karasiq.shadowcloud.storage.gdrive

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext

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

  private[this] val folderIdCache = TrieMap.empty[Path, FileId]
  private[this] val filesCache = TrieMap.empty[Path, FileList].withDefaultValue(Vector.empty)

  def getOrCreateFolderId(path: Path): FileId = {
    folderIdCache.getOrElseUpdate(path, service.createFolder(path.nodes).id)
  }

  def getFolderId(path: Path): FileId = {
    folderIdCache.getOrElseUpdate(path, service.folder(path.nodes).id)
  }

  def getFileIds(path: Path, cached: Boolean = true): FileIds = {
    val cachedFiles = filesCache(path)
    def getActualFiles() = service.files(getFolderId(path.parent), path.name).toVector

    if (cached && cachedFiles.nonEmpty) {
      cachedFiles.map(_.id)
    } else {
      val files = getActualFiles()
      if (files.isEmpty) filesCache -= path else filesCache += path → files
      files.map(_.id)
    }
  }

  def isFileExists(path: Path): Boolean = {
    def isExists = getFileIds(path).nonEmpty
    def isReallyExists = getFileIds(path, cached = false).nonEmpty

    if (isExists) true else isReallyExists
  }
}
