package com.karasiq.shadowcloud.index.utils

import com.karasiq.shadowcloud.utils.MergeUtil
import com.karasiq.shadowcloud.utils.MergeUtil.SplitDecider

trait DiffMergeDecider[T] {
  def apply(leftCreates: Set[T], leftDeletes: Set[T],
            rightCreates: Set[T], rightDeletes: Set[T],
            splitDecider: SplitDecider[T]): (Set[T], Set[T])
}

//noinspection ConvertExpressionToSAM
object DiffMergeDecider {
  def idempotent[T]: DiffMergeDecider[T] = new DiffMergeDecider[T] {
    def apply(leftCreates: Set[T], leftDeletes: Set[T],
              rightCreates: Set[T], rightDeletes: Set[T],
              splitDecider: SplitDecider[T]) = {
      MergeUtil.splitSets(leftCreates ++ rightCreates, leftDeletes ++ rightDeletes, splitDecider)
    }
  }

  def leftWins[T]: DiffMergeDecider[T] = new DiffMergeDecider[T] {
    def apply(leftCreates: Set[T], leftDeletes: Set[T],
              rightCreates: Set[T], rightDeletes: Set[T],
              splitDecider: SplitDecider[T]) = {
      MergeUtil.splitSets(rightCreates -- leftDeletes ++ leftCreates, rightDeletes -- leftCreates ++ leftDeletes, splitDecider)
    }
  }

  def rightWins[T]: DiffMergeDecider[T] = new DiffMergeDecider[T] {
    def apply(leftCreates: Set[T], leftDeletes: Set[T],
              rightCreates: Set[T], rightDeletes: Set[T],
              splitDecider: SplitDecider[T]) = {
      leftWins[T](rightCreates, rightDeletes, leftCreates, leftDeletes, splitDecider)
    }
  }
}
