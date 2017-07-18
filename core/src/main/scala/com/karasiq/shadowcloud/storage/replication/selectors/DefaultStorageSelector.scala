package com.karasiq.shadowcloud.storage.replication.selectors

import scala.util.Random

import com.karasiq.shadowcloud.actors.context.RegionContext
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.replication.{ChunkWriteAffinity, StorageSelector}
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.{ChunkStatus, WriteStatus}
import com.karasiq.shadowcloud.storage.replication.StorageStatusProvider.StorageStatus
import com.karasiq.shadowcloud.utils.Utils

class DefaultStorageSelector(region: RegionContext) extends StorageSelector {
  def available(toWrite: Long = 0): Seq[StorageStatus] = {
    region.storages.storages.filter(s ⇒ s.health.online && s.health.canWrite > toWrite).toSeq
  }

  def forIndexWrite(diff: IndexDiff): Seq[StorageStatus] = {
    val writable = available(1024) // At least 1KB
    Utils.takeOrAll(writable, region.config.indexReplicationFactor)
  }

  def forRead(status: ChunkStatus): Option[StorageStatus] = {
    available().find(storage ⇒ status.availability.isWritten(storage.id) && !status.availability.isFailed(storage.id))
  }

  def forWrite(chunk: ChunkStatus): ChunkWriteAffinity = {
    def generateList(): Seq[String] = {
      def canWriteChunk(storage: StorageStatus): Boolean = {
        !chunk.availability.isWriting(storage.id) && !chunk.waitingChunk.contains(storage.dispatcher)
      }

      val writeSize = chunk.chunk.checksum.encSize
      available(writeSize)
        .filter(canWriteChunk)
        .map(_.id)
    }

    def selectFromList(storages: Seq[String]): Seq[String] = {
      val randomized = Random.shuffle(storages.distinct)
      Utils.takeOrAll(randomized, region.config.dataReplicationFactor)
    }

    val generatedList = generateList()
    chunk.writeStatus match {
      case WriteStatus.Pending(affinity) ⇒
        val newList = affinity.mandatory.filterNot(chunk.availability.isFailed) ++ generatedList
        affinity.copy(mandatory = selectFromList(newList))

      case _ ⇒
        ChunkWriteAffinity(selectFromList(generatedList))
    }
  }
}
