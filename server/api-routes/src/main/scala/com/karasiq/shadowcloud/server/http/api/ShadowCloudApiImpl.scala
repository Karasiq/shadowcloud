package com.karasiq.shadowcloud.server.http.api

import scala.concurrent.Future
import scala.language.implicitConversions

import akka.Done
import akka.stream.scaladsl.Sink

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.actors.internal.RegionTracker
import com.karasiq.shadowcloud.actors.utils.ActorState
import com.karasiq.shadowcloud.actors.RegionGC.GCStrategy
import com.karasiq.shadowcloud.api.ShadowCloudApi
import com.karasiq.shadowcloud.config.{ConfigProps, RegionConfig, SerializedProps}
import com.karasiq.shadowcloud.index.diffs.FolderIndexDiff
import com.karasiq.shadowcloud.index.files.FileVersions
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.keys.{KeyId, KeySet}
import com.karasiq.shadowcloud.model.utils.{IndexScope, RegionStateReport}
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.replication.ChunkWriteAffinity
import com.karasiq.shadowcloud.streams.region.RegionRepairStream

private[server] final class ShadowCloudApiImpl(sc: ShadowCloudExtension) extends ShadowCloudApi {
  import sc.implicits.{executionContext, materializer}

  // -----------------------------------------------------------------------
  // Regions
  // -----------------------------------------------------------------------
  def getRegions() = {
    def toSerializableStorageStatus(storage: RegionTracker.StorageStatus) = {
      RegionStateReport.StorageStatus(storage.storageId, ConfigProps.fromConfig(storage.storageProps.rootConfig, json = false),
        storage.actorState == ActorState.Suspended, storage.regions)
    }

    def toSerializableRegionStatus(region: RegionTracker.RegionStatus) = {
      RegionStateReport.RegionStatus(region.regionId, ConfigProps.fromConfig(region.regionConfig.rootConfig, json = false),
        region.actorState == ActorState.Suspended, region.storages)
    }

    sc.ops.supervisor.getSnapshot().map { snapshot ⇒
      val storages = snapshot.storages.map { case (storageId, storage) ⇒
        (storageId, toSerializableStorageStatus(storage))
      }

      val regions = snapshot.regions.map { case (regionId, region) ⇒
        (regionId, toSerializableRegionStatus(region))
      }

      RegionStateReport(regions, storages)
    }
  }

  def getRegion(regionId: RegionId) = {
    getRegions().map(_.regions(regionId))
  }

  def getStorage(storageId: StorageId) = {
    getRegions().map(_.storages(storageId))
  }

  def createRegion(regionId: RegionId, regionConfig: SerializedProps) = {
    sc.ops.supervisor.createRegion(regionId, RegionConfig(ConfigProps.toConfig(regionConfig)))
    getRegion(regionId)
  }

  def createStorage(storageId: StorageId, storageProps: SerializedProps) = {
    val resolvedProps = StorageProps(ConfigProps.toConfig(storageProps))
    sc.ops.supervisor.createStorage(storageId, resolvedProps)
    getStorage(storageId)
  }

  def suspendRegion(regionId: RegionId) = {
    sc.ops.supervisor.suspendRegion(regionId)
  }

  def suspendStorage(storageId: StorageId) = {
    sc.ops.supervisor.suspendStorage(storageId)
  }

  def resumeRegion(regionId: RegionId) = {
    sc.ops.supervisor.resumeRegion(regionId)
  }

  def resumeStorage(storageId: StorageId) = {
    sc.ops.supervisor.resumeStorage(storageId)
  }

  def registerStorage(regionId: RegionId, storageId: StorageId) = {
    sc.ops.supervisor.register(regionId, storageId)
  }

  def unregisterStorage(regionId: RegionId, storageId: StorageId) = {
    sc.ops.supervisor.unregister(regionId, storageId)
  }

  def deleteRegion(regionId: RegionId) = {
    for {
      region ← getRegion(regionId)
      _ ← sc.ops.supervisor.deleteRegion(regionId)
    } yield region
  }

  def deleteStorage(storageId: StorageId) = {
    for {
      storage ← getStorage(storageId)
      _ ← sc.ops.supervisor.deleteStorage(storageId)
    } yield storage
  }

  def synchronizeStorage(storageId: StorageId, regionId: RegionId) = {
    sc.ops.storage.synchronize(storageId, regionId)
  }

  def synchronizeRegion(regionId: RegionId) = {
    sc.ops.region.synchronize(regionId)
  }

  def collectGarbage(regionId: RegionId, delete: Boolean) = {
    sc.ops.region.collectGarbage(regionId, if (delete) GCStrategy.Delete else GCStrategy.Preview)
  }

  def compactIndex(storageId: StorageId, regionId: RegionId) = {
    sc.ops.storage.compactIndex(storageId, regionId)
    synchronizeStorage(storageId, regionId)
  }

  def compactIndexes(regionId: RegionId) = {
    sc.ops.region.compactIndex(regionId)
    synchronizeRegion(regionId)
  }


  def getRegionHealth(regionId: RegionId) = {
    sc.ops.region.getHealth(regionId)
  }

  def getStorageHealth(storageId: StorageId) = {
    sc.ops.storage.getHealth(storageId)
  }

  def getStorageTypes() = {
    Future.successful(sc.modules.storage.storageTypes)
  }

  def getDefaultStorageConfig(storageType: String) = {
    Future.successful(sc.modules.storage.defaultConfig(storageType))
  }

  // -----------------------------------------------------------------------
  // Keys
  // -----------------------------------------------------------------------
  def getKeys() = {
    sc.keys.provider.getKeyChain().map(_.withoutKeys)
  }

  def modifyKey(keyId: KeyId, regionSet: Set[RegionId], forEncryption: Boolean, forDecryption: Boolean) = {
    sc.keys.provider.modifyKeySet(keyId, regionSet, forEncryption, forDecryption)
  }

  def generateKey(regionSet: Set[RegionId], forEncryption: Boolean, forDecryption: Boolean, props: SerializedProps) = {
    val (encMethod, signMethod) = sc.keys.getGenerationProps(props)
    Future(sc.keys.generateKeySet(encMethod, signMethod))(sc.executionContexts.cryptography)
      .flatMap(sc.keys.provider.addKeySet(_, regionSet, forEncryption, forDecryption))
  }

  def addKey(key: KeySet, regionSet: Set[RegionId], forEncryption: Boolean, forDecryption: Boolean) = {
    sc.keys.provider.addKeySet(key, regionSet, forEncryption, forDecryption)
  }

  // -----------------------------------------------------------------------
  // Folders
  // -----------------------------------------------------------------------
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

  // -----------------------------------------------------------------------
  // Files
  // -----------------------------------------------------------------------
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
    } yield Done
  }

  def copyFile(regionId: RegionId, file: File, newPath: Path, scope: IndexScope = IndexScope.default) = {
    getFullFile(regionId, file, scope)
      .map(_.copy(path = newPath))
      .flatMap(createFile(regionId, _))
  }

  def createFile(regionId: RegionId, file: File) = {
    require(file.checksum.size == 0 || file.chunks.nonEmpty, "File is empty")
    sc.ops.region.writeIndex(regionId, FolderIndexDiff.createFiles(file)).map(_ ⇒ Done)
  }

  def deleteFiles(regionId: RegionId, path: Path) = {
    sc.ops.region.deleteFiles(regionId, path)
  }

  def deleteFile(regionId: RegionId, file: File) = {
    for {
      actualFile ← getFullFile(regionId, file, IndexScope.default)
      _ ← sc.ops.region.deleteFiles(regionId, actualFile)
    } yield actualFile
  }


  def repairFile(regionId: RegionId, file: File, storages: Seq[StorageId], scope: IndexScope) = {
    for {
      actualFile ← getFullFile(regionId, file, scope) if actualFile.chunks.nonEmpty
      _ ← sc.ops.background.repair(regionId, RegionRepairStream.Strategy.SetAffinity(ChunkWriteAffinity(storages)), actualFile.chunks)
    } yield Done
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  private[this] def getFullFile(regionId: RegionId, file: File, scope: IndexScope): Future[File] = {
    if (file.isEmpty) {
      this.getFile(regionId, file.path, file.id, dropChunks = false, scope)
    } else {
      Future.successful(file)
    }
  }

  private[this] implicit def implicitUnitToFutureDone(unit: Unit): Future[Done] = {
    Future.successful(Done)
  }
}
