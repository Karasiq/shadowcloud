package com.karasiq.shadowcloud.server.http.api

import scala.concurrent.Future

import akka.stream.scaladsl.Sink

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.api.ShadowCloudApi
import com.karasiq.shadowcloud.index.diffs.FolderIndexDiff
import com.karasiq.shadowcloud.index.files.FileVersions
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.utils.IndexScope

private[server] final class ShadowCloudApiImpl(sc: ShadowCloudExtension) extends ShadowCloudApi {
  import sc.implicits.{executionContext, materializer}

  def getFolder(regionId: RegionId, path: Path, dropChunks: Boolean = true, scope: IndexScope = IndexScope.default) = {
    val future = sc.ops.region.getFolder(regionId, path, scope)
    if (dropChunks) future.map(_.withoutChunks) else future
  }

  def createFolder(regionId: RegionId, path: Path) = {
    sc.ops.region.createFolder(regionId, path)
      .flatMap(_ ⇒ getFolder(regionId, path))
  }

  def deleteFolder(regionId: RegionId, path: Path) = {
    sc.ops.region.deleteFolder(regionId, path)
  }

  def copyFolder(regionId: RegionId, path: Path, newPath: Path, scope: IndexScope = IndexScope.default) = {
    for {
      index ← sc.ops.region.getFolderIndex(regionId, scope)
      _ ← sc.ops.region.writeIndex(regionId, FolderIndexDiff.copyFolder(index, path, newPath))
      newFolder ← sc.ops.region.getFolder(regionId, newPath)
    } yield newFolder
  }

  def mergeFolder(regionId: RegionId, folder: Folder) = {
    for {
      _ ← sc.ops.region.writeIndex(regionId, FolderIndexDiff.createFolders(folder))
      newFolder ← sc.ops.region.getFolder(regionId, folder.path)
    } yield newFolder
  }

  def getFiles(regionId: RegionId, path: Path, dropChunks: Boolean = true, scope: IndexScope = IndexScope.default) = {
    val filesFuture = sc.ops.region.getFiles(regionId, path, scope)
    if (dropChunks) filesFuture.map(_.map(_.withoutChunks)) else filesFuture
  }

  def getFile(regionId: RegionId, path: Path, id: FileId, dropChunks: Boolean = true, scope: IndexScope = IndexScope.default) = {
    getFiles(regionId, path, dropChunks, scope).map(FileVersions.withId(id, _))
  }

  def getFileAvailability(regionId: RegionId, file: File, scope: IndexScope = IndexScope.default) = {
    getFullFile(regionId, file, scope).flatMap(sc.ops.region.getFileAvailability(regionId, _))
  }

  def getFileMetadata(regionId: RegionId, fileId: FileId, disposition: Metadata.Tag.Disposition) = {
    sc.streams.metadata.read(regionId, fileId, disposition).runWith(Sink.seq)
  }

  def copyFiles(regionId: RegionId, path: Path, newPath: Path, scope: IndexScope = IndexScope.default) = {
    for {
      files ← sc.ops.region.getFiles(regionId, path, scope)
      _ ← sc.ops.region.writeIndex(regionId, FolderIndexDiff.copyFiles(files, newPath))
      newFiles ← sc.ops.region.getFiles(regionId, newPath)
    } yield newFiles
  }

  def copyFile(regionId: RegionId, file: File, newPath: Path, scope: IndexScope = IndexScope.default) = {
    val fullFileFuture = getFullFile(regionId, file, scope)
    fullFileFuture.flatMap(file ⇒ this.createFile(regionId, file.copy(path = newPath)))
  }

  def createFile(regionId: RegionId, file: File) = {
    require(file.checksum.size == 0 || file.chunks.nonEmpty, "File is empty")
    for {
      _ ← sc.ops.region.writeIndex(regionId, FolderIndexDiff.createFiles(file))
      newFiles ← sc.ops.region.getFiles(regionId, file.path)
    } yield newFiles
  }

  private[this] def getFullFile(regionId: RegionId, file: File, scope: IndexScope): Future[File] = {
    if (file.isEmpty) {
      this.getFile(regionId, file.path, file.id, dropChunks = false, scope)
    } else {
      Future.successful(file)
    }
  }
}
