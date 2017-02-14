package com.karasiq.shadowcloud.storage

import com.karasiq.shadowcloud.index.{ChunkIndex, FolderIndex, IndexDiff}
import com.karasiq.shadowcloud.storage.internal.DefaultIndexMerger

import scala.collection.SortedMap
import scala.language.postfixOps

trait IndexMerger[T] {
  def lastSequenceNr: T
  def chunks: ChunkIndex
  def folders: FolderIndex
  def diffs: SortedMap[T, IndexDiff]
  def mergedDiff: IndexDiff
  def pending: IndexDiff
  def add(sequenceNr: T, diff: IndexDiff): Unit
  def remove(sequenceNrs: Set[T]): Unit
  def addPending(diff: IndexDiff): Unit
  def removePending(diff: IndexDiff): Unit
  def clear(): Unit
}

object IndexMerger {
  final case class RegionKey(timestamp: Long = 0L, indexId: String = "", sequenceNr: Long = 0L)
  object RegionKey {
    implicit val ordering: Ordering[RegionKey] = Ordering.by(key ⇒ (key.timestamp, key.indexId, key.sequenceNr))
    val zero: RegionKey = RegionKey()
  }

  /**
    * Sequential index merger
    */
  def apply(): IndexMerger[Long] = {
    new DefaultIndexMerger(0L)
  }

  /**
    * Multi-sequence index merger
    */
  def region: IndexMerger[RegionKey] = {
    new DefaultIndexMerger[RegionKey](RegionKey.zero)(RegionKey.ordering)
  }
}