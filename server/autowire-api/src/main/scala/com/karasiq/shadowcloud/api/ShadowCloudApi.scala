package com.karasiq.shadowcloud.api

import java.util.UUID

import akka.Done
import akka.util.ByteString
import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.keys.{KeyChain, KeyId, KeySet}
import com.karasiq.shadowcloud.model.utils._
import com.karasiq.shadowcloud.ui.Challenge

import scala.concurrent.Future

trait ShadowCloudApi {
  // -----------------------------------------------------------------------
  // Regions
  // -----------------------------------------------------------------------
  def getRegions(): Future[RegionStateReport]
  def getRegion(regionId: RegionId): Future[RegionStateReport.RegionStatus]
  def getStorage(storageId: StorageId): Future[RegionStateReport.StorageStatus]
  def createRegion(regionId: RegionId, regionConfig: SerializedProps = SerializedProps.empty): Future[RegionStateReport.RegionStatus]
  def createStorage(storageId: StorageId, storageProps: SerializedProps): Future[RegionStateReport.StorageStatus]
  def suspendRegion(regionId: RegionId): Future[Done]
  def suspendStorage(storageId: StorageId): Future[Done]
  def resumeRegion(regionId: RegionId): Future[Done]
  def resumeStorage(storageId: StorageId): Future[Done]
  def registerStorage(regionId: RegionId, storageId: StorageId): Future[Done]
  def unregisterStorage(regionId: RegionId, storageId: StorageId): Future[Done]
  def deleteRegion(regionId: RegionId): Future[RegionStateReport.RegionStatus]
  def deleteStorage(storageId: StorageId): Future[RegionStateReport.StorageStatus]
  def synchronizeStorage(storageId: StorageId, regionId: RegionId): Future[SyncReport]
  def synchronizeRegion(regionId: RegionId): Future[Map[StorageId, SyncReport]]
  def collectGarbage(regionId: RegionId, delete: Boolean = false): Future[GCReport]
  def compactIndex(storageId: StorageId, regionId: RegionId): Future[SyncReport]
  def compactIndexes(regionId: RegionId): Future[Map[StorageId, SyncReport]]
  def repairRegion(regionId: RegionId, storages: Seq[StorageId]): Future[Done]
  def getRegionHealth(regionId: RegionId): Future[RegionHealth]
  def getStorageHealth(storageId: StorageId): Future[StorageHealth]
  def getStorageTypes(): Future[Set[String]]
  def getDefaultStorageConfig(storageType: String): Future[SerializedProps]

  // -----------------------------------------------------------------------
  // Keys
  // -----------------------------------------------------------------------
  def getKeys(): Future[KeyChain]
  def modifyKey(keyId: KeyId, regionSet: Set[RegionId] = Set.empty, forEncryption: Boolean, forDecryption: Boolean): Future[Done]
  def generateKey(regionSet: Set[RegionId] = Set.empty, forEncryption: Boolean = true, forDecryption: Boolean = true, props: SerializedProps = SerializedProps.empty): Future[KeySet]
  def addKey(key: KeySet, regionSet: Set[RegionId] = Set.empty, forEncryption: Boolean = true, forDecryption: Boolean = true): Future[KeySet]

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
  def listFileMetadata(regionId: RegionId, fileId: FileId): Future[Set[Metadata.Tag.Disposition]]
  def getFileMetadata(regionId: RegionId, fileId: FileId, disposition: Metadata.Tag.Disposition): Future[Seq[Metadata]]
  def copyFiles(regionId: RegionId, path: Path, newPath: Path, scope: IndexScope = IndexScope.default): Future[Done]
  def copyFile(regionId: RegionId, file: File, newPath: Path, scope: IndexScope = IndexScope.default): Future[Done]
  def createFile(regionId: RegionId, file: File): Future[Done]
  def deleteFiles(regionId: RegionId, path: Path): Future[Set[File]]
  def deleteFile(regionId: RegionId, file: File): Future[File]
  def repairFile(regionId: RegionId, file: File, storages: Seq[StorageId], scope: IndexScope = IndexScope.default): Future[Done]

  // -----------------------------------------------------------------------
  // Сhallenges
  // -----------------------------------------------------------------------
  def getChallenges(): Future[Seq[Challenge]]
  def solveChallenge(id: UUID, answer: ByteString): Future[Done]
}
