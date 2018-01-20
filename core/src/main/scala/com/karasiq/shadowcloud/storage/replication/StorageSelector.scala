package com.karasiq.shadowcloud.storage.replication

import scala.util.Try

import com.karasiq.shadowcloud.actors.context.RegionContext
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus
import com.karasiq.shadowcloud.storage.replication.RegionStorageProvider.RegionStorage

object StorageSelector {
  def fromClass(ssClass: Class[_ <: StorageSelector])(implicit regionContext: RegionContext): StorageSelector = {
    Try(ssClass.getConstructor(classOf[RegionContext]).newInstance(regionContext))
      .getOrElse(ssClass.newInstance())
  }
}

trait StorageSelector {
  def available(toWrite: Long = 0): Seq[RegionStorage]
  def forIndexWrite(diff: IndexDiff): Seq[RegionStorage]
  def forRead(status: ChunkStatus): Option[RegionStorage]
  def forWrite(chunk: ChunkStatus): ChunkWriteAffinity
}
