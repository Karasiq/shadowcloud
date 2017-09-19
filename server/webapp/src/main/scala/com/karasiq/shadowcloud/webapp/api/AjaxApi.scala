package com.karasiq.shadowcloud.webapp.api

import scala.concurrent.ExecutionContext

import autowire._

import com.karasiq.shadowcloud.api.{SCApiMeta, ShadowCloudApi}
import com.karasiq.shadowcloud.api.js.SCAjaxApiClient
import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.metadata.Metadata.Tag
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.utils.IndexScope

object AjaxApi extends ShadowCloudApi with FileApi with SCApiMeta {
  private[api] val clientFactory = SCAjaxApiClient

  type EncodingT = clientFactory.EncodingT
  val encoding = clientFactory.encoding
  val payloadContentType = clientFactory.payloadContentType

  import encoding.implicits._

  private[this] val apiClient = clientFactory[ShadowCloudApi]
  private[this] implicit val ec: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  def getRegions() = {
    apiClient.getRegions().call()
  }

  def getRegion(regionId: RegionId) = {
    apiClient.getRegion(regionId).call()
  }
  
  def getStorage(storageId: StorageId) = {
    apiClient.getStorage(storageId).call()
  }

  def createRegion(regionId: RegionId, regionConfig: SerializedProps) = {
    apiClient.createRegion(regionId, regionConfig).call()
  }

  def createStorage(storageId: StorageId, storageProps: SerializedProps) = {
    apiClient.createStorage(storageId, storageProps).call()
  }

  def suspendRegion(regionId: RegionId) = {
    apiClient.suspendRegion(regionId).call()
  }

  def suspendStorage(storageId: StorageId) = {
    apiClient.suspendStorage(storageId).call()
  }

  def resumeRegion(regionId: RegionId) = {
    apiClient.resumeRegion(regionId).call()
  }

  def resumeStorage(storageId: StorageId) = {
    apiClient.resumeStorage(storageId).call()
  }

  def registerStorage(regionId: RegionId, storageId: StorageId) = {
    apiClient.registerStorage(regionId, storageId).call()
  }

  def unregisterStorage(regionId: RegionId, storageId: StorageId) = {
    apiClient.unregisterStorage(regionId, storageId).call()
  }

  def deleteRegion(regionId: RegionId) = {
    apiClient.deleteRegion(regionId).call()
  }

  def deleteStorage(storageId: StorageId) = {
    apiClient.deleteStorage(storageId).call()
  }

  def synchronizeStorage(storageId: StorageId, regionId: RegionId) = {
    apiClient.synchronizeStorage(storageId, regionId).call()
  }

  def synchronizeRegion(regionId: RegionId) = {
    apiClient.synchronizeRegion(regionId).call()
  }

  def collectGarbage(regionId: RegionId, delete: Boolean) = {
    apiClient.collectGarbage(regionId, delete).call()
  }

  def compactIndex(storageId: StorageId, regionId: RegionId) = {
    apiClient.compactIndex(storageId, regionId).call()
  }

  def compactIndexes(regionId: RegionId) = {
    apiClient.compactIndexes(regionId).call()
  }

  def getFolder(regionId: RegionId, path: Path, dropChunks: Boolean = true, scope: IndexScope = IndexScope.default) = {
    apiClient.getFolder(regionId, path, dropChunks, scope).call()
  }

  def createFolder(regionId: RegionId, path: Path) = {
    apiClient.createFolder(regionId, path).call()
  }

  def deleteFolder(regionId: RegionId, path: Path) = {
    apiClient.deleteFolder(regionId, path).call()
  }

  def copyFolder(regionId: RegionId, path: Path, newPath: Path, scope: IndexScope = IndexScope.default) = {
    apiClient.copyFolder(regionId, path, newPath, scope).call()
  }

  def mergeFolder(regionId: RegionId, folder: Folder) = {
    apiClient.mergeFolder(regionId, folder).call()
  }

  def getFiles(regionId: RegionId, path: Path, dropChunks: Boolean = true, scope: IndexScope = IndexScope.default) = {
    apiClient.getFiles(regionId, path, dropChunks, scope).call()
  }

  def getFile(regionId: RegionId, path: Path, id: FileId, dropChunks: Boolean, scope: IndexScope) = {
    apiClient.getFile(regionId, path, id, dropChunks, scope).call()
  }

  def getFileAvailability(regionId: RegionId, file: File, scope: IndexScope = IndexScope.default) = {
    apiClient.getFileAvailability(regionId, file, scope).call()
  }

  def getFileMetadata(regionId: RegionId, fileId: FileId, disposition: Tag.Disposition) = {
    apiClient.getFileMetadata(regionId, fileId, disposition).call()
  }

  def copyFiles(regionId: RegionId, path: Path, newPath: Path, scope: IndexScope = IndexScope.default) = {
    apiClient.copyFiles(regionId, path, newPath, scope).call()
  }

  def copyFile(regionId: RegionId, file: File, newPath: Path, scope: IndexScope) = {
    apiClient.copyFile(regionId, file, newPath, scope).call()
  }

  def createFile(regionId: RegionId, file: File) = {
    apiClient.createFile(regionId, file).call()
  }

  def deleteFiles(regionId: RegionId, path: Path) = {
    apiClient.deleteFiles(regionId, path).call()
  }

  def deleteFile(regionId: RegionId, file: File) = {
    apiClient.deleteFile(regionId, file).call()
  }
}
