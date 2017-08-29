package com.karasiq.shadowcloud.exceptions

import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.model.{Chunk, Path}

sealed abstract class RegionException(message: String = null, cause: Throwable = null)
  extends SCException(message, cause)

object RegionException {
  // -----------------------------------------------------------------------
  // Not found errors
  // -----------------------------------------------------------------------
  final case class ChunkNotFound(chunk: Chunk)
    extends RegionException("Chunk not found: " + chunk) with SCException.NotFound

  final case class FileNotFound(path: Path)
    extends RegionException("File not found: " + path) with SCException.NotFound

  final case class DirectoryNotFound(path: Path)
    extends RegionException("Directory not found: " + path) with SCException.NotFound

  // -----------------------------------------------------------------------
  // IO errors
  // -----------------------------------------------------------------------
  sealed abstract class ChunkIOError(message: String = null, cause: Throwable = null)
    extends RegionException(message, cause) with SCException.IOError with SCException.ChunkAssociated

  sealed abstract class IndexIOError(message: String = null, cause: Throwable = null)
    extends RegionException(message, cause) with SCException.IOError

  final case class ChunkWriteFailed(chunk: Chunk, cause: Throwable)
    extends ChunkIOError("Chunk write failed: " + chunk, cause) with SCException.WrappedError

  final case class ChunkReadFailed(chunk: Chunk, cause: Throwable)
    extends ChunkIOError("Chunk read failed: " + chunk, cause) with SCException.WrappedError

  final case class ChunkRepairFailed(chunk: Chunk, cause: Throwable)
    extends ChunkIOError("Chunk repair failed: " + chunk, cause) with SCException.WrappedError

  final case class IndexWriteFailed(diff: IndexDiff, cause: Throwable)
    extends IndexIOError("Index write failed: " + diff, cause) with SCException.WrappedError with SCException.DiffAssociated

  final case class ChunkUnavailable(chunk: Chunk)
    extends ChunkIOError("Chunk unavailable: " + chunk)
}