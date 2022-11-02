package com.karasiq.shadowcloud.index.utils

trait HasWithoutKeys {
  type Repr

  /** Drops cryptographic keys from entity
    * @return Entity without keys
    */
  def withoutKeys: Repr
}
