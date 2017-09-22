package com.karasiq.shadowcloud.index.utils

import scala.language.postfixOps

trait HasWithoutData {
  type Repr

  /**
    * Drops binary data payload from entity
    * @return Entity without data
    */
  def withoutData: Repr
}
