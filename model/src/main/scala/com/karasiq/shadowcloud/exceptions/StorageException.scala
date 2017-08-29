package com.karasiq.shadowcloud.exceptions

import com.karasiq.shadowcloud.model.Path

sealed abstract class StorageException(message: String = null, cause: Throwable = null)
  extends SCException(message, cause) with SCException.IOError with SCException.PathAssociated

object StorageException {
  final case class NotFound(path: Path)
    extends StorageException("Not found: " + path) with SCException.NotFound

  final case class AlreadyExists(path: Path)
    extends StorageException("Already exists: " + path) with SCException.AlreadyExists

  final case class IOFailure(path: Path, cause: Throwable)
    extends StorageException("IO failure: " + path, cause) with SCException.WrappedError
}