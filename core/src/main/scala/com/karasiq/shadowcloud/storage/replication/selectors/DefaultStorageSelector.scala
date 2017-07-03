package com.karasiq.shadowcloud.storage.replication.selectors

import com.karasiq.shadowcloud.actors.context.RegionContext
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.replication.StorageSelector
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus
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
    available().find(storage ⇒ status.hasChunk.contains(storage.id))
  }

  def forWrite(chunk: ChunkStatus): Seq[StorageStatus] = {
    def canWriteChunk(storage: StorageStatus): Boolean = {
      !chunk.hasChunk.contains(storage.id) &&
        !chunk.writingChunk.contains(storage.id) &&
        !chunk.waitingChunk.contains(storage.dispatcher)
    }

    def limitToRF(storages: Seq[StorageStatus]): Seq[StorageStatus] = {
      if (region.config.dataReplicationFactor > 0) {
        val hasChunk = (chunk.hasChunk ++ chunk.writingChunk).size
        storages.take(hasChunk - region.config.dataReplicationFactor)
      } else {
        storages
      }
    }

    val writeSize = chunk.chunk.checksum.encSize
    limitToRF(available(writeSize).filter(canWriteChunk))
  }

  def isFinished(chunk: ChunkStatus): Boolean = {
    val needWrites = if (region.config.dataReplicationFactor > 0) {
      region.config.dataReplicationFactor
    } else {
      math.max(1, available(chunk.chunk.checksum.encSize).size)
    }
    val hasChunk = chunk.hasChunk.size
    hasChunk >= needWrites
  }
}
