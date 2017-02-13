package com.karasiq.shadowcloud.storage

import com.karasiq.shadowcloud.index.{ChunkIndex, FolderDecider, FolderIndex, IndexDiff}
import com.karasiq.shadowcloud.utils.MergeUtil.Decider

import scala.collection.{SortedMap, mutable}
import scala.language.postfixOps

trait IndexMerger {
  def lastSequenceNr: Long
  def chunks: ChunkIndex
  def folders: FolderIndex
  def diffs: SortedMap[Long, IndexDiff]
  def mergedDiff: IndexDiff
  def pending: IndexDiff
  def add(sequenceNr: Long, diff: IndexDiff): Unit
  def addPending(diff: IndexDiff): Unit
  def removePending(diff: IndexDiff): Unit
  def clear(): Unit
}

object IndexMerger {
  private final class DefaultIndexMerger extends IndexMerger {
    private[this] var _diffs = mutable.SortedMap.empty[Long, IndexDiff]
    private[this] var _chunks = ChunkIndex.empty
    private[this] var _folders = FolderIndex.empty
    private[this] var _merged = IndexDiff.empty
    private[this] var _pending = IndexDiff.empty

    def lastSequenceNr: Long = if (_diffs.isEmpty) 0 else _diffs.lastKey
    def chunks: ChunkIndex = _chunks
    def folders: FolderIndex = _folders
    def diffs: SortedMap[Long, IndexDiff] = _diffs
    def mergedDiff: IndexDiff = _merged
    def pending: IndexDiff = _pending

    def add(sequenceNr: Long, diff: IndexDiff): Unit = {
      require(!_diffs.contains(sequenceNr), "Invalid sequence number")
      val lastDiff = _diffs.lastOption
      _diffs += sequenceNr → diff
      if (lastDiff.isEmpty || lastDiff.forall(_._1 < sequenceNr)) {
        applyDiff(diff)
      } else {
        rebuildIndex()
      }
      removePending(_merged)
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

  def apply(): IndexMerger = {
    new DefaultIndexMerger
  }
}