package com.karasiq.shadowcloud.model.utils

import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.utils.GCReport.{RegionGCState, StorageGCState}
import com.karasiq.shadowcloud.utils.Utils

@SerialVersionUID(0L)
final case class GCReport(regionId: RegionId, regionState: RegionGCState, storageStates: Map[StorageId, StorageGCState])

object GCReport {
  @SerialVersionUID(0L)
  final case class RegionGCState(orphanedChunks: Set[Chunk],
                                 oldFiles: Set[File],
                                 expiredMetadata: Set[FileId]) extends HasEmpty {

    def isEmpty: Boolean = orphanedChunks.isEmpty && oldFiles.isEmpty && expiredMetadata.isEmpty

    override def toString: String = {
      s"RegionGCState(orphaned chunks = [${Utils.printChunkHashes(orphanedChunks)}], old files = [${Utils.printValues(oldFiles)}], expired metadata = [${Utils.printValues(expiredMetadata)}])"
    }
  }

  @SerialVersionUID(0L)
  final case class StorageGCState(notIndexed: Set[ChunkId], notExisting: Set[Chunk]) extends HasEmpty {
    def isEmpty: Boolean = notIndexed.isEmpty && notExisting.isEmpty

    override def toString: String = {
      s"StorageGCState(not indexed chunks = [${Utils.printHashes(notIndexed)}], lost chunks = [${Utils.printChunkHashes(notExisting)}])"
    }
  }
}