package com.karasiq.shadowcloud.index.utils

trait HasWithoutChunks {
  type Repr

  /**
    * Drops data chunks from entity
    * @return Entity without chunks information
    */
  def withoutChunks: Repr
}
