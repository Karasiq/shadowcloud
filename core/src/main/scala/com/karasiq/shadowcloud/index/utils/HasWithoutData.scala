package com.karasiq.shadowcloud.index.utils

import scala.language.postfixOps

trait HasWithoutData {
  type Repr
  def withoutData: Repr
}
