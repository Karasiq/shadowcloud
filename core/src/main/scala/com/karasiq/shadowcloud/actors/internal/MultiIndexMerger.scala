package com.karasiq.shadowcloud.actors.internal

import com.karasiq.shadowcloud.index.FolderIndex
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.utils.IndexMerger

import scala.collection.{SortedMap, mutable}
import scala.language.postfixOps

private[actors] object MultiIndexMerger {
  def apply()(implicit ord: Ordering[Long]): MultiIndexMerger[Long] = {
    new MultiIndexMerger(0L)
  }
}

private[actors] final class MultiIndexMerger[@specialized(Long) T: Ordering](zeroKey: T) {
  private[this] val regionIndexes = mutable.AnyRefMap.empty[String, IndexMerger[T]]
    .withDefault(_ ⇒ IndexMerger.create(zeroKey))

  // Global
  def subIndexes: Map[String, IndexMerger[T]] = {
    regionIndexes.toMap 
  }

  def subDiffs: Map[String, SortedMap[T, IndexDiff]] = {
    subIndexes.mapValues(_.diffs)
  }

  def clear(): Unit = {
    regionIndexes.clear()
  }

  // Local
  def lastSequenceNr(region: String): T = {
    regionIndexes(region).lastSequenceNr
  }

  def folders(region: String): FolderIndex = {
    regionIndexes(region).folders
  }

  def diffs(region: String): SortedMap[T, IndexDiff] = {
    regionIndexes(region).diffs
  }

  def mergedDiff(region: String): IndexDiff = {
    regionIndexes(region).mergedDiff
  }

  def pending(region: String): IndexDiff = {
    regionIndexes(region).pending
  }

  def add(region: String, sequenceNr: T, diff: IndexDiff): Unit = {
    val index = regionIndexes(region)
    index.add(sequenceNr, diff)
    if (!regionIndexes.contains(region)) regionIndexes += region → index
  }

  def delete(region: String, sequenceNrs: Set[T]): Unit = {
    regionIndexes.get(region).foreach(_.delete(sequenceNrs))
  }

  def addPending(region: String, diff: IndexDiff): Unit = {
    val index = regionIndexes(region)
    index.addPending(diff)
    if (!regionIndexes.contains(region)) regionIndexes += region → index
  }

  def deletePending(region: String, diff: IndexDiff): Unit = {
    regionIndexes.get(region).foreach(_.deletePending(diff))
  }

  def clear(region: String): Unit = {
    regionIndexes -= region
  }
}
