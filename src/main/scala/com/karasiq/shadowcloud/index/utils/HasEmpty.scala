package com.karasiq.shadowcloud.index.utils

import scala.language.postfixOps

trait HasEmpty {
  def isEmpty: Boolean
  final def nonEmpty: Boolean = !isEmpty
}
