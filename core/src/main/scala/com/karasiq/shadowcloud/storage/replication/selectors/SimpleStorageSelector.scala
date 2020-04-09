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
    val randomize         = selectorConfig.withDefault(false, _.getBoolean("randomize"))
    val indexWriteMinSize = selectorConfig.withDefault[Long](SizeUnit.KB * 100, _.getBytes("index-write-min-size"))
    val priority          = selectorConfig.withDefault(Nil, _.getStrings("priority"))
    val writeExclude      = selectorConfig.withDefault(Set.empty[String], _.getStringSet("write-exclude"))
    val writeInclude      = selectorConfig.withDefault(Set.empty[String], _.getStringSet("write-include"))
  }

  def available(toWrite: Long = 0): Seq[RegionStorage] = {
    region.storages.storages.filter(_.health.canWrite(toWrite)).toVector
  }

  def forIndexWrite(diff: IndexDiff): Seq[RegionStorage] = {
    val writable = available(settings.indexWriteMinSize)
    (Utils.takeOrAll(writable, settings.indexRF) ++ writable.filter(s => settings.writeInclude(s.id))).distinct
  }

  def forRead(status: ChunkStatus): Option[RegionStorage] = {
    val readable = available().filter(storage ⇒ status.availability.isWritten(storage.id) && !status.availability.isFailed(storage.id))
    tryRandomize(readable).headOption
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
        .sortBy(storage ⇒ chunk.availability.isFailed(storage.id))
        .map(_.id)
    }

    val generatedList = generateList()
    chunk.writeStatus match {
      case WriteStatus.Pending(affinity) ⇒
        val newList = affinity.mandatory.filterNot(chunk.availability.isFailed) ++ generatedList
        affinity.copy(mandatory = (selectStoragesToWrite(newList) ++ settings.writeInclude).distinct)

      case _ ⇒
        ChunkWriteAffinity(selectStoragesToWrite(generatedList))
    }
  }

  protected def tryRandomize[T](storages: Seq[T]): Seq[T] = {
    if (settings.randomize) Random.shuffle(storages) else storages
  }

  protected def sortStorages(storages: Seq[StorageId]): Seq[StorageId] = {
    val hasIndex           = settings.priority.toSet
    val (sorted, unsorted) = storages.distinct.partition(hasIndex)
    sorted.sortBy(settings.priority.indexOf) ++ tryRandomize(unsorted)
  }

  protected def selectStoragesToWrite(storages: Seq[StorageId]): Seq[StorageId] = {
    Utils.takeOrAll(sortStorages(storages.distinct), settings.dataRF)
  }
}
