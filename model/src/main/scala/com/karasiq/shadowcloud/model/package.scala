package com.karasiq.shadowcloud

import java.util.UUID

package object model {
  type RegionId = String
  type StorageId = String

  type SequenceNr = Long
  object SequenceNr {
    val zero = 0L
  }

  type FileId = java.util.UUID
  object FileId {
    def create(): FileId = {
      UUID.randomUUID()
    }
  }

  type ChunkId = akka.util.ByteString
}
