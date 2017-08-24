package com.karasiq.shadowcloud

package object model {
  type RegionId = String
  type StorageId = String

  type SequenceNr = Long
  object SequenceNr {
    val zero = 0L
  }

  type ChunkId = akka.util.ByteString
}
