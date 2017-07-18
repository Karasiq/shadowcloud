package com.karasiq.shadowcloud.storage.replication

import com.karasiq.shadowcloud.actors.context.RegionContext
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus
import com.karasiq.shadowcloud.storage.replication.StorageStatusProvider.StorageStatus

object StorageSelector {
  def fromClass(ssClass: Class[_ <: StorageSelector])(implicit regionContext: RegionContext): StorageSelector = {
    ssClass.getConstructor(classOf[RegionContext]).newInstance(regionContext)
  }
}

trait StorageSelector {
  def available(toWrite: Long = 0): Seq[StorageStatus]
  def forIndexWrite(diff: IndexDiff): Seq[StorageStatus]
  def forRead(status: ChunkStatus): Option[StorageStatus]
  def forWrite(chunk: ChunkStatus): ChunkWriteAffinity
}
