package com.karasiq.shadowcloud.actors.events

import com.karasiq.shadowcloud.actors.internal.StringEventBus
import com.karasiq.shadowcloud.index.{Chunk, IndexDiff}

import scala.language.postfixOps

object StorageEvent {
  sealed trait Event
  case class IndexLoaded(diffs: Seq[(Long, IndexDiff)]) extends Event
  case class PendingIndexUpdated(diff: IndexDiff) extends Event
  case class IndexUpdated(sequenceNr: Long, diff: IndexDiff, remote: Boolean) extends Event
  case class ChunkWritten(chunk: Chunk) extends Event

  case class StorageEnvelope(storageId: String, event: Event)

  val stream = new StringEventBus[StorageEnvelope](_.storageId)
}


