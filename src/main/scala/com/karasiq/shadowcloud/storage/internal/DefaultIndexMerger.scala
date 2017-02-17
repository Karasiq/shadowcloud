package com.karasiq.shadowcloud.storage.internal

import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.index.utils.FolderDecider
import com.karasiq.shadowcloud.index.{ChunkIndex, FolderIndex}
import com.karasiq.shadowcloud.storage.IndexMerger
import com.karasiq.shadowcloud.utils.MergeUtil.Decider

import scala.collection.{SortedMap, mutable}
import scala.language.postfixOps

private[storage] final class DefaultIndexMerger[@specialized(Long) T](firstKey: T)(implicit ord: Ordering[T]) extends IndexMerger[T] {
  private[this] var _diffs = mutable.SortedMap.empty[T, IndexDiff]
  private[this] var _chunks = ChunkIndex.empty
  private[this] var _folders = FolderIndex.empty
  private[this] var _merged = IndexDiff.empty
  private[this] var _pending = IndexDiff.empty

  def lastSequenceNr: T = if (_diffs.isEmpty) firstKey else _diffs.lastKey
  def chunks: ChunkIndex = _chunks
  def folders: FolderIndex = _folders
  def diffs: SortedMap[T, IndexDiff] = _diffs
  def mergedDiff: IndexDiff = _merged
  def pending: IndexDiff = _pending

  def add(sequenceNr: T, diff: IndexDiff): Unit = {
    _diffs.get(sequenceNr) match {
      case Some(`diff`) ⇒
        // Pass

      case Some(existing) ⇒
        throw new IllegalArgumentException(s"Diff conflict: $existing / $diff")

      case None ⇒
        val lastDiff = _diffs.lastOption
        _diffs += sequenceNr → diff
        if (lastDiff.isEmpty || lastDiff.forall(kv ⇒ ord.gt(sequenceNr, kv._1))) {
          applyDiff(diff)
        } else {
          rebuildIndex()
        }
        removePending(diff)
    }
  }
  
  def remove(sequenceNrs: Set[T]): Unit = {
    _diffs --= sequenceNrs
    rebuildIndex()
  }

  def addPending(diff: IndexDiff): Unit = {
    _pending = pending.merge(diff)
  }

  def removePending(diff: IndexDiff): Unit = {
    _pending = pending.diff(diff, Decider.keepLeft, FolderDecider.mutualExclude, Decider.keepLeft)
  }

  def clear(): Unit = {
    _chunks = ChunkIndex.empty
    _folders = FolderIndex.empty
    _merged = IndexDiff.empty
    _pending = IndexDiff.empty
    _diffs.clear()
  }

  private[this] def applyDiff(diff: IndexDiff): Unit = {
    _chunks = _chunks.patch(diff.chunks)
    _folders = _folders.patch(diff.folders)
    _merged = _merged.merge(diff)
  }

  private[this] def rebuildIndex(): Unit = {
    _chunks = ChunkIndex.empty
    _folders = FolderIndex.empty
    _merged = IndexDiff.empty
    _pending = IndexDiff.empty
    _diffs.values.foreach(applyDiff)
  }
}
