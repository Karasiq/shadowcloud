package com.karasiq.shadowcloud.exceptions

import java.io.IOException

sealed abstract class StorageException(message: String = null, cause: Throwable = null) extends IOException(message, cause)

object StorageException {
  case class NotFound(path: String) extends StorageException(s"Not found: $path")
  case class AlreadyExists(path: String) extends StorageException(s"Already exists: $path")
  case class IOFailure(path: String, cause: Throwable) extends StorageException(s"IO failure: $path", cause)
}