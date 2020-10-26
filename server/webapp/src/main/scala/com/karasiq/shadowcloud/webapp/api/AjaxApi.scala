package com.karasiq.shadowcloud.webapp.api

import java.util.UUID

import akka.Done
import akka.util.ByteString
import autowire._
import com.karasiq.shadowcloud.api.js.SCAjaxBooPickleApiClient
import com.karasiq.shadowcloud.api.{SCApiMeta, ShadowCloudApi}
import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.metadata.Metadata.Tag
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.keys.{KeyId, KeySet}
import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.ui.Challenge
import com.karasiq.shadowcloud.webapp.context.AppContext

import scala.concurrent.{ExecutionContext, Future}

object AjaxApi extends ShadowCloudApi with FileApi with SCApiMeta {

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[api] val clientFactory = SCAjaxBooPickleApiClient

  type EncodingT = clientFactory.EncodingT
  val encoding           = clientFactory.encoding
  val payloadContentType = clientFactory.payloadContentType

  import encoding.implicits._ // Should not be deleted
  private[this] implicit val implicitExecutionContext: ExecutionContext = AppContext.JsExecutionContext

  private[this] val apiClient = clientFactory[ShadowCloudApi]

  // -----------------------------------------------------------------------
  // Regions
  // -----------------------------------------------------------------------
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

  def repairRegion(regionId: RegionId, storages: Seq[StorageId]) = {
    apiClient.repairRegion(regionId, storages).call()
  }

  def getRegionHealth(regionId: RegionId) = {
    apiClient.getRegionHealth(regionId).call()
  }

  def getStorageHealth(storageId: StorageId) = {
    apiClient.getStorageHealth(storageId).call()
  }

  def getStorageTypes() = {
    apiClient.getStorageTypes().call()
  }

  def getDefaultStorageConfig(storageType: String) = {
    apiClient.getDefaultStorageConfig(storageType).call()
  }

  // -----------------------------------------------------------------------
  // Keys
  // -----------------------------------------------------------------------
  def getKeys() = {
    apiClient.getKeys().call()
  }

  def modifyKey(keyId: KeyId, regionSet: Set[RegionId], forEncryption: Boolean, forDecryption: Boolean) = {
    apiClient.modifyKey(keyId, regionSet, forEncryption, forDecryption).call()
  }

  def generateKey(regionSet: Set[RegionId], forEncryption: Boolean, forDecryption: Boolean, props: SerializedProps) = {
    apiClient.generateKey(regionSet, forEncryption, forDecryption, props).call()
  }

  def addKey(key: KeySet, regionSet: Set[RegionId], forEncryption: Boolean, forDecryption: Boolean) = {
    apiClient.addKey(key, regionSet, forEncryption, forDecryption).call()
  }

  // -----------------------------------------------------------------------
  // Folders
  // -----------------------------------------------------------------------
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

  // -----------------------------------------------------------------------
  // Files
  // -----------------------------------------------------------------------
  def getFiles(regionId: RegionId, path: Path, dropChunks: Boolean = true, scope: IndexScope = IndexScope.default) = {
    apiClient.getFiles(regionId, path, dropChunks, scope).call()
  }

  def getFile(regionId: RegionId, path: Path, id: FileId, dropChunks: Boolean, scope: IndexScope) = {
    apiClient.getFile(regionId, path, id, dropChunks, scope).call()
  }

  def getFileAvailability(regionId: RegionId, file: File, scope: IndexScope = IndexScope.default) = {
    apiClient.getFileAvailability(regionId, file, scope).call()
  }

  def listFileMetadata(regionId: RegionId, fileId: FileId): Future[Set[Tag.Disposition]] = {
    apiClient.listFileMetadata(regionId, fileId).call()
  }

  def getFileMetadata(regionId: RegionId, fileId: FileId, disposition: Tag.Disposition): Future[Seq[Metadata]] = {
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

  def repairFile(regionId: RegionId, file: File, storages: Seq[StorageId], scope: IndexScope) = {
    apiClient.repairFile(regionId, file, storages, scope).call()
  }

  override def getChallenges(): Future[Seq[Challenge]] = {
    apiClient.getChallenges().call()
  }

  override def solveChallenge(id: UUID, answer: ByteString): Future[Done] = {
    apiClient.solveChallenge(id, answer).call()
  }
}
