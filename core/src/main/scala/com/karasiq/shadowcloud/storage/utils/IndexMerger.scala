package com.karasiq.shadowcloud.storage.utils

import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.index.{ChunkIndex, FolderIndex}
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
  def delete(sequenceNrs: Set[T]): Unit
  def addPending(diff: IndexDiff): Unit
  def deletePending(diff: IndexDiff): Unit
  def clear(): Unit
}

object IndexMerger {
  final case class RegionKey(timestamp: Long = 0L, indexId: String = "", sequenceNr: Long = 0L) {
    override def toString: String = {
      s"($indexId/$sequenceNr at $timestamp)"
    }

    override def hashCode(): Int = {
      (indexId, sequenceNr).hashCode()
    }

    override def equals(obj: scala.Any): Boolean = obj match {
      case RegionKey(_, `indexId`, `sequenceNr`) ⇒
        true

      case _ ⇒
        false
    }
  }

  object RegionKey {
    implicit val ordering: Ordering[RegionKey] = Ordering.by(key ⇒ (key.timestamp, key.indexId, key.sequenceNr))
    val zero: RegionKey = RegionKey()
  }

  def create[T: Ordering](zeroKey: T): IndexMerger[T] = {
    new DefaultIndexMerger[T](zeroKey)
  }

  /**
    * Sequential index merger
    */
  def apply(): IndexMerger[Long] = create(0L)

  /**
    * Multi-sequence index merger
    */
  def region: IndexMerger[RegionKey] = create(RegionKey.zero)(RegionKey.ordering)
}