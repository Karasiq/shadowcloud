package com.karasiq.shadowcloud.index.utils

trait HasEmpty {
  def isEmpty: Boolean
  final def nonEmpty: Boolean = !isEmpty
}
