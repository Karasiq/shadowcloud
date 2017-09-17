package com.karasiq.shadowcloud.api

import scala.concurrent.Future

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.utils.{FileAvailability, IndexScope}

trait ShadowCloudApi {
  // -----------------------------------------------------------------------
  // Folders
  // -----------------------------------------------------------------------
  def getFolder(regionId: RegionId, path: Path, dropChunks: Boolean = true, scope: IndexScope = IndexScope.default): Future[Folder]
  def createFolder(regionId: RegionId, path: Path): Future[Folder]
  def deleteFolder(regionId: RegionId, path: Path): Future[Folder]
  def copyFolder(regionId: RegionId, path: Path, newPath: Path, scope: IndexScope = IndexScope.default): Future[Folder]
  def mergeFolder(regionId: RegionId, folder: Folder): Future[Folder]

  // -----------------------------------------------------------------------
  // Files
  // -----------------------------------------------------------------------
  def getFiles(regionId: RegionId, path: Path, dropChunks: Boolean = true, scope: IndexScope = IndexScope.default): Future[Set[File]]
  def getFile(regionId: RegionId, path: Path, id: FileId, dropChunks: Boolean = true, scope: IndexScope = IndexScope.default): Future[File]
  def getFileAvailability(regionId: RegionId, file: File, scope: IndexScope = IndexScope.default): Future[FileAvailability]
  def getFileMetadata(regionId: RegionId, fileId: FileId, disposition: Metadata.Tag.Disposition): Future[Seq[Metadata]]
  def copyFiles(regionId: RegionId, path: Path, newPath: Path, scope: IndexScope = IndexScope.default): Future[Set[File]]
  def copyFile(regionId: RegionId, file: File, newPath: Path, scope: IndexScope = IndexScope.default): Future[Set[File]]
  def createFile(regionId: RegionId, file: File): Future[Set[File]]
  def deleteFiles(regionId: RegionId, path: Path): Future[Set[File]]
  def deleteFile(regionId: RegionId, file: File): Future[File]
}
