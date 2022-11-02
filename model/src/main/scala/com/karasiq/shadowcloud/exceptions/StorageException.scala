package com.karasiq.shadowcloud.exceptions

import com.karasiq.shadowcloud.model.Path

sealed abstract class StorageException(message: String = null, cause: Throwable = null)
    extends SCException(message, cause)
    with SCException.IOError
    with SCException.PathAssociated

object StorageException {
  final case class NotFound(path: Path, cause: Throwable = null) extends StorageException("Not found: " + path, cause) with SCException.NotFound

  final case class AlreadyExists(path: Path, cause: Throwable = null)
      extends StorageException("Already exists: " + path, cause)
      with SCException.AlreadyExists

  final case class IOFailure(path: Path, cause: Throwable) extends StorageException("IO failure: " + path, cause) with SCException.WrappedError
}
