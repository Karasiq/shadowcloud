package com.karasiq.shadowcloud.actors.events

import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.model.{Chunk, RegionId, SequenceNr}
import com.karasiq.shadowcloud.storage.utils.IndexMerger

object StorageEvents {
  sealed trait Event
  case class IndexLoaded(regionId: RegionId, state: IndexMerger.State[Long])                            extends Event
  case class PendingIndexUpdated(regionId: RegionId, diff: IndexDiff)                                   extends Event
  case class IndexUpdated(regionId: RegionId, sequenceNr: SequenceNr, diff: IndexDiff, remote: Boolean) extends Event
  case class IndexDeleted(regionId: RegionId, sequenceNrs: Set[Long])                                   extends Event
  case class ChunkWritten(path: ChunkPath, chunk: Chunk)                                                extends Event
  case class HealthUpdated(health: StorageHealth)                                                       extends Event
}
