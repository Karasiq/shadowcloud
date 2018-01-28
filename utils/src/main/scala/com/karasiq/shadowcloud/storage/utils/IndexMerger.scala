package com.karasiq.shadowcloud.storage.utils

import scala.collection.SortedMap
import scala.language.postfixOps

import com.karasiq.shadowcloud.index.{ChunkIndex, FolderIndex}
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model.{SequenceNr, StorageId}
import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.storage.utils.internal.DefaultIndexMerger

// TODO: UUID, time query
private[shadowcloud] trait IndexMerger[T] extends HasEmpty {
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
  def load(state: IndexMerger.State[T]): Unit
  def clear(): Unit

  override def isEmpty: Boolean = diffs.isEmpty
}

private[shadowcloud] object IndexMerger {
  def apply[T](implicit ko: IndexKeyOrdering[T]): IndexMerger[T] = {
    create[T](ko.zero)(ko.ordering)
  }

  def createState[@specialized(Long) T](index: IndexMerger[T]): State[T] = {
    State(index.diffs.toVector, index.pending)
  }

  def restore[@specialized(Long) T](state: State[T])(implicit cs: IndexKeyOrdering[T]): IndexMerger[T] = {
    val index = this.apply[T]
    index.load(state)
    index
  }

  def create[@specialized(Long) T: Ordering](zeroKey: T): IndexMerger[T] = {
    new DefaultIndexMerger[T](zeroKey)
  }

  def copy[T: IndexKeyOrdering](index: IndexMerger[T]) = {
    restore(createState(index))
  }

  /**
    * Sequential index merger
    */
  def sequential(): IndexMerger[SequenceNr] = {
    this.apply[SequenceNr]
  }

  /**
    * Multi-sequence index merger
    */
  def region(): IndexMerger[RegionKey] = {
    this.apply[RegionKey]
  }

  def scopedView[T](index: IndexMerger[T], scope: IndexScope)(implicit ko: IndexKeyOrdering[T]): IndexMerger[T] = scope match {
    case IndexScope.Current ⇒
      index

    case IndexScope.Persisted ⇒
      val filteredState = createState(index).copy(pending = IndexDiff.empty)
      this.restore(filteredState)

    case IndexScope.UntilSequenceNr(sequenceNr) ⇒
      val state = this.createState(index)
      val filteredState = state.copy(diffs = state.diffs.filter(kv ⇒ ko.sequenceNr(kv._1) <= sequenceNr), pending = IndexDiff.empty)
      this.restore(filteredState)

    case IndexScope.UntilTime(timestamp) ⇒
      val state = this.createState(index)
      val filteredState = state.copy(diffs = state.diffs.filter(kv ⇒ ko.timestamp(kv._1) <= timestamp), pending = IndexDiff.empty)
      this.restore(filteredState)
  }

  def compact[T: IndexKeyOrdering](index: IndexMerger[T], scope: IndexScope = IndexScope.default): IndexDiff = {
    val view = scopedView(index, scope)
    view.mergedDiff.merge(view.pending).creates
  }

  @SerialVersionUID(0L)
  final case class RegionKey(timestamp: Long = 0L, storageId: StorageId = "", sequenceNr: SequenceNr = 0L) {
    override def toString: String = {
      s"($storageId/$sequenceNr at $timestamp)"
    }

    override def hashCode(): Int = {
      (storageId, sequenceNr).hashCode()
    }

    override def equals(obj: scala.Any): Boolean = obj match {
      case RegionKey(_, `storageId`, `sequenceNr`) ⇒
        true

      case _ ⇒
        false
    }
  }

  object RegionKey {
    implicit val ordering: Ordering[RegionKey] = Ordering.by(key ⇒ (key.timestamp, key.storageId, key.sequenceNr))
    val zero: RegionKey = RegionKey()
  }

  @SerialVersionUID(0L)
  final case class State[@specialized(Long) T](diffs: Seq[(T, IndexDiff)] = Vector.empty, pending: IndexDiff = IndexDiff.empty)

  object State {
    val empty = new State()
  }

  trait IndexKeyOrdering[T] {
    implicit def ordering: Ordering[T]
    def zero: T
    def sequenceNr(key: T): SequenceNr
    def timestamp(key: T): Long
  }

  object IndexKeyOrdering {
    implicit val sequenceNrOrdering: IndexKeyOrdering[SequenceNr] = new IndexKeyOrdering[SequenceNr] {
      implicit val ordering: Ordering[SequenceNr] = Ordering.Long
      def zero: SequenceNr = SequenceNr.zero
      def sequenceNr(key: SequenceNr): SequenceNr = key
      def timestamp(key: SequenceNr): SequenceNr = key
    }

    implicit val regionKeyOrdering: IndexKeyOrdering[RegionKey] = new IndexKeyOrdering[RegionKey] {
      implicit def ordering: Ordering[RegionKey] = RegionKey.ordering
      def zero = RegionKey.zero
      def sequenceNr(key: RegionKey) = key.sequenceNr
      def timestamp(key: RegionKey) = key.timestamp
    }
  }
}