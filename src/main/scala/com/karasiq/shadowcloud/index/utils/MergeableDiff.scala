package com.karasiq.shadowcloud.index.utils

import scala.language.postfixOps

trait MergeableDiff[Self] extends Mergeable[Self, Self] {
  def reverse: Self
  def creates: Self
  def deletes: Self

  override def patch(diff: Self): Self = {
    merge(diff)
  }
}
