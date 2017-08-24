package com.karasiq.shadowcloud.actors.utils

import akka.util.ByteString

import com.karasiq.shadowcloud.index.{Chunk, File}
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model.FileId
import com.karasiq.shadowcloud.utils.Utils

case class RegionGCState(orphanedChunks: Set[Chunk],
                         oldFiles: Set[File],
                         expiredMetadata: Set[FileId]) extends HasEmpty {

  def isEmpty: Boolean = orphanedChunks.isEmpty && oldFiles.isEmpty && expiredMetadata.isEmpty

  override def toString: String = {
    s"GCState(orphaned chunks = [${Utils.printChunkHashes(orphanedChunks)}], old files = [${Utils.printValues(oldFiles)}], expired metadata = [${Utils.printValues(expiredMetadata)}])"
  }
}

case class StorageGCState(notIndexed: Set[ByteString],
                          notExisting: Set[Chunk]) extends HasEmpty {

  def isEmpty: Boolean = notIndexed.isEmpty && notExisting.isEmpty

  override def toString: String = {
    s"StorageGCState(not indexed chunks = [${Utils.printHashes(notIndexed)}], lost chunks = [${Utils.printChunkHashes(notExisting)}])"
  }
}