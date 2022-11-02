package com.karasiq.shadowcloud.index.utils

trait HasWithoutData {
  type Repr

  /** Drops binary data payload from entity
    * @return Entity without data
    */
  def withoutData: Repr
}
