package com.karasiq.shadowcloud.actors.events

import scala.language.postfixOps

import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey

object RegionEvents {
  // Events
  sealed trait Event
  case class IndexUpdated(sequenceNr: RegionKey, diff: IndexDiff) extends Event
  case class IndexDeleted(keys: Set[RegionKey]) extends Event
  case class ChunkWritten(storageId: StorageId, chunk: Chunk) extends Event
}