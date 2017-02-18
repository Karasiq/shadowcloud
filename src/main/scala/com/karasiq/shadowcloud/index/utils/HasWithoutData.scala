package com.karasiq.shadowcloud.index.utils

import scala.language.postfixOps

trait HasWithoutData[T] {
  def withoutData: T
}
