package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model.{RegionId, SCEntity, SequenceNr}

// Wrapped data
@SerialVersionUID(0L)
final case class IndexData(regionId: RegionId, sequenceNr: SequenceNr, diff: IndexDiff) extends SCEntity with HasEmpty {
  def isEmpty: Boolean = diff.isEmpty
}

object IndexData {
  val empty = IndexData(RegionId.empty, SequenceNr.zero, IndexDiff.empty)
}
