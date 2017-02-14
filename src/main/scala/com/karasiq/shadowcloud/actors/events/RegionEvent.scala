package com.karasiq.shadowcloud.actors.events

import com.karasiq.shadowcloud.index.{Chunk, IndexDiff}
import com.karasiq.shadowcloud.storage.IndexMerger.RegionKey

import scala.language.postfixOps

object RegionEvent {
  // Events
  sealed trait Event
  case class IndexUpdated(sequenceNr: RegionKey, diff: IndexDiff) extends Event
  case class ChunkWritten(storageId: String, chunk: Chunk) extends Event

  case class RegionEnvelope(regionId: String, event: Event)

  val stream = new StringEventBus[RegionEnvelope](_.regionId)
}