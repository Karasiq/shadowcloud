package com.karasiq.shadowcloud.storage

import com.karasiq.shadowcloud.index.IndexDiff
import com.karasiq.shadowcloud.utils.MergeUtil.Decider

import scala.language.postfixOps

trait IndexMerger {
  def stored: IndexDiff
  def pending: IndexDiff
  def add(diff: IndexDiff): Unit
  def addPending(diff: IndexDiff): Unit
  def removePending(diff: IndexDiff): Unit
}

object IndexMerger {
  private final class DefaultIndexMerger extends IndexMerger {
    private[this] var _stored = IndexDiff.empty
    private[this] var _pending = IndexDiff.empty

    def stored: IndexDiff = _stored
    def pending: IndexDiff = _pending

    def add(diff: IndexDiff): Unit = {
      _stored = stored.merge(diff)
      removePending(diff)
    }
    
    def addPending(diff: IndexDiff): Unit = {
      _pending = pending.merge(diff)
    }

    def removePending(diff: IndexDiff): Unit = {
      _pending = pending.diff(diff, Decider.keepLeft, Decider.keepLeft, Decider.keepLeft, Decider.keepLeft)
    }
  }

  def apply(): IndexMerger = new DefaultIndexMerger
}