package com.karasiq.shadowcloud.model.utils

import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model.SequenceNr
import com.karasiq.shadowcloud.utils.Utils

@SerialVersionUID(0L)
final case class SyncReport(
    read: Map[SequenceNr, IndexDiff] = Map.empty,
    written: Map[SequenceNr, IndexDiff] = Map.empty,
    deleted: Set[SequenceNr] = Set.empty
) extends HasEmpty {
  def isEmpty: Boolean = read.isEmpty && written.isEmpty && deleted.isEmpty

  override def toString: String = {
    s"SyncReport(read = [${Utils.printValues(read)}], written = [${Utils.printValues(written)}], deleted = [${Utils.printValues(deleted, 50)}])"
  }
}

object SyncReport {
  val empty = SyncReport()
}
