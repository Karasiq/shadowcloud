package com.karasiq.shadowcloud.model

package object keys {
  type KeyId = java.util.UUID
  object KeyId {
    val empty = new KeyId(0L, 0L)

    def create(): KeyId = {
      java.util.UUID.randomUUID()
    }
  }
}
