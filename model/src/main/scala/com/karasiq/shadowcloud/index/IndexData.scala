package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model.{RegionId, SequenceNr}

// Wrapped data
case class IndexData(regionId: RegionId, sequenceNr: SequenceNr, diff: IndexDiff) extends HasEmpty {
  def isEmpty: Boolean = diff.isEmpty
}

object IndexData {
  val empty = IndexData("", 0, IndexDiff.empty)
}
