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
  def apply(): IndexMerger[Long] = {
    new DefaultIndexMerger(0L)
  }

  def multiSeq: IndexMerger[(String, Long)] = {
    new DefaultIndexMerger[(String, Long)](("", 0L))
  }
}