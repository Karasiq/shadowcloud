package com.karasiq.shadowcloud.actors.utils

import akka.util.ByteString

import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.utils.Utils

case class GCState(orphaned: Set[Chunk], notIndexed: Set[ByteString], notExisting: Set[Chunk]) extends HasEmpty {
  def isEmpty: Boolean = orphaned.isEmpty && notIndexed.isEmpty && notExisting.isEmpty

  override def toString: String = {
    s"GCState(orphaned = [${Utils.printChunkHashes(orphaned)}], not indexed = [${Utils.printHashes(notIndexed)}], not existing = [${Utils.printChunkHashes(notExisting)}])"
  }
}