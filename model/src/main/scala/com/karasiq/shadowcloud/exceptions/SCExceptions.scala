package com.karasiq.shadowcloud.exceptions

import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.index.diffs.IndexDiff

object SCExceptions {
  final case class ChunkDataIsEmpty(chunk: Chunk)
    extends SCException("Chunk data is empty: " + chunk) with SCException.ChunkAssociated

  final case class ChunkConflict(chunk: Chunk, offeredChunk: Chunk)
    extends SCException("Chunk conflict: " + chunk + " / " + offeredChunk) with SCException.ChunkAssociated

  final case class ChunkVerifyError(chunk: Chunk, cause: Throwable = null)
    extends SCException("Chunk verify error: " + chunk, cause) with SCException.ChunkAssociated

  final case class DiffConflict(diff: IndexDiff, offeredDiff: IndexDiff)
    extends SCException("Diff conflict: " + diff + " / " + offeredDiff) with SCException.DiffAssociated
}
