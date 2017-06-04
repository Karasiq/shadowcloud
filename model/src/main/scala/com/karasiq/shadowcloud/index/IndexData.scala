package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.index.utils.HasEmpty

// Wrapped data
case class IndexData(region: String, sequenceNr: Long, diff: IndexDiff) extends HasEmpty {
  def isEmpty: Boolean = diff.isEmpty
}

object IndexData {
  val empty = IndexData("", 0, IndexDiff.empty)
}
