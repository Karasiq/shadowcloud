package com.karasiq.shadowcloud.storage.replication.selectors

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.common.memory.SizeUnit
import com.karasiq.shadowcloud.actors.context.RegionContext
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.{ChunkStatus, WriteStatus}
import com.karasiq.shadowcloud.storage.replication.RegionStorageProvider.RegionStorage
import com.karasiq.shadowcloud.storage.replication.{ChunkWriteAffinity, StorageSelector}
import com.karasiq.shadowcloud.utils.Utils

import scala.util.Random

class SimpleStorageSelector(region: RegionContext) extends StorageSelector {
  protected object settings extends ConfigImplicits {
    val selectorConfig    = region.config.rootConfig.getConfigIfExists("simple-selector")
    val indexRF           = region.config.indexReplicationFactor
    val dataRF            = region.config.dataReplicationFactor
    val writeRandomize    = selectorConfig.withDefault(false, _.getBoolean("write-randomize"))
    val readRandomize     = selectorConfig.withDefault(false, _.getBoolean("read-randomize"))
    val indexWriteMinSize = selectorConfig.withDefault[Long](SizeUnit.KB * 10, _.getBytes("index-write-min-size"))
    val writePriority     = selectorConfig.withDefault(Nil, _.getStrings("write-priority"))
    val readPriority      = selectorConfig.withDefault(Nil, _.getStrings("priority"))
    val writeExclude      = selectorConfig.withDefault(Set.empty[String], _.getStringSet("write-exclude"))
    val writeInclude      = selectorConfig.withDefault(Set.empty[String], _.getStringSet("write-include"))
    val readExclude       = selectorConfig.withDefault(Set.empty[String], _.getStringSet("read-exclude"))
    val reservedSize      = selectorConfig.withDefault(20L * SizeUnit.MB, _.getMemorySize("reserved-size").toBytes)
    val asyncWriteLimit   = selectorConfig.withDefault(100 * SizeUnit.KB, _.getMemorySize("async-write-limit").toBytes)
  }

  def available(toWrite: Long = 0): Seq[RegionStorage] = {
    region.storages.storages
      .filter(_.health.canWrite(toWrite + settings.reservedSize))
      .toVector
  }

  def forIndexWrite(diff: IndexDiff): Seq[RegionStorage] = {
    val writable = available(settings.indexWriteMinSize)
    (Utils.takeOrAll(writable, settings.indexRF) ++ writable.filter(s ⇒ settings.writeInclude(s.id))).distinct
  }

  def forRead(status: ChunkStatus): Option[RegionStorage] = {
    val readable = available().filter(storage ⇒ status.availability.isWritten(storage.id) && !status.availability.isFailed(storage.id))
    sortStoragesForRead(readable.map(_.id)).headOption
      .map(region.storages.getStorage)
  }

  def forWrite(chunk: ChunkStatus): ChunkWriteAffinity = {
    def generateList(): Seq[String] = {
      def canWriteChunk(storage: RegionStorage): Boolean = {
        !settings.writeExclude.contains(storage.id) &&
        !chunk.availability.isWriting(storage.id) &&
        !chunk.waitingChunk.contains(storage.dispatcher)
      }

      val writeSize = chunk.chunk.checksum.encSize
      available(writeSize)
        .filter(canWriteChunk)
        .sortBy(storage ⇒ (chunk.availability.isFailed(storage.id), -storage.health.writableSpace / SizeUnit.GB))
        .map(_.id)
    }

    val generatedList      = generateList()
    val canWriteEventually = chunk.chunk.checksum.encSize <= settings.asyncWriteLimit
    chunk.writeStatus match {
      case WriteStatus.Pending(affinity) ⇒
        val currentList = if (canWriteEventually) affinity.mandatory ++ affinity.eventually else affinity.mandatory
        val newList     = currentList.filterNot(chunk.availability.isFailed) ++ generatedList
        val selected    = selectStoragesToWrite(newList).distinct
        if (canWriteEventually) affinity.copy(mandatory = Nil, eventually = selected)
        else affinity.copy(mandatory = selected)

      case _ ⇒
        if (canWriteEventually) ChunkWriteAffinity(eventually = selectStoragesToWrite(generatedList))
        else ChunkWriteAffinity(selectStoragesToWrite(generatedList))
    }
  }

  protected def sortStoragesForRead(storages: Seq[StorageId]): Seq[StorageId] = {
    def tryRandomize[T](storages: Seq[T]): Seq[T] = {
      if (settings.readRandomize) Random.shuffle(storages) else storages
    }

    val hasIndex           = settings.readPriority.toSet
    val (sorted, unsorted) = storages.distinct.partition(hasIndex)
    sorted.sortBy(settings.readPriority.indexOf) ++ tryRandomize(unsorted)
  }

  protected def sortStoragesForWrite(storages: Seq[StorageId]): Seq[StorageId] = {
    def tryRandomize[T](storages: Seq[T]): Seq[T] = {
      if (settings.writeRandomize) Random.shuffle(storages) else storages
    }

    val hasIndex           = settings.writePriority.toSet
    val (sorted, unsorted) = storages.distinct.partition(hasIndex)
    sorted.sortBy(settings.writePriority.indexOf) ++ tryRandomize(unsorted)
  }

  protected def selectStoragesToWrite(storages: Seq[StorageId]): Seq[StorageId] = {
    val notExplicitlyIncluded = storages.filterNot(settings.writeInclude).distinct
    val sorted                = sortStoragesForWrite(notExplicitlyIncluded)
    Utils.takeOrAll(sorted, settings.dataRF) ++ settings.writeInclude
  }
}
