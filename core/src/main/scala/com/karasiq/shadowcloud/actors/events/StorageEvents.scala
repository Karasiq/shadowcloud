package com.karasiq.shadowcloud.actors.events

import scala.language.postfixOps

import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.StorageHealth
import com.karasiq.shadowcloud.storage.utils.IndexMerger

object StorageEvents {
  sealed trait Event
  case class IndexLoaded(regionId: String, state: IndexMerger.State[Long]) extends Event
  case class PendingIndexUpdated(regionId: String, diff: IndexDiff) extends Event
  case class IndexUpdated(regionId: String, sequenceNr: Long, diff: IndexDiff, remote: Boolean) extends Event
  case class IndexDeleted(regionId: String, sequenceNrs: Set[Long]) extends Event
  case class ChunkWritten(path: ChunkPath, chunk: Chunk) extends Event
  case class HealthUpdated(health: StorageHealth) extends Event
}


