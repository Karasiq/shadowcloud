package com.karasiq.shadowcloud.index.utils

trait HasWithoutChunks {
  type Repr
  def withoutChunks: Repr
}
