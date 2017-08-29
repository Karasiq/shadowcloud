package com.karasiq.shadowcloud

package object model {
  type RegionId = String
  object RegionId {
    val empty = ""
  }

  type StorageId = String
  object StorageId {
    val empty = ""
  }

  type SequenceNr = Long
  object SequenceNr {
    val zero = 0L
  }

  type FileId = java.util.UUID
  object FileId {
    val empty = new FileId(0L, 0L)

    def create(): FileId = {
      java.util.UUID.randomUUID()
    }
  }

  type ChunkId = akka.util.ByteString
  object ChunkId {
    val empty = akka.util.ByteString.empty
  }
}
