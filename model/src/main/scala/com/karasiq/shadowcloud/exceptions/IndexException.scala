package com.karasiq.shadowcloud.exceptions

import java.io.IOException
import java.util.UUID

sealed abstract class IndexException(message: String = null, cause: Throwable = null) extends IOException(message, cause)

object IndexException {
  case class KeyMissing(keyId: UUID, cause: Throwable = null) extends IndexException("Key missing: " + keyId, cause)
}