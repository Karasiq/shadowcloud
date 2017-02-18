package com.karasiq.shadowcloud.index.utils

import scala.language.postfixOps

trait Mergeable[Self, Diff] {
  def merge(right: Self): Self
  def diff(right: Self): Diff
  def patch(diff: Diff): Self
}
