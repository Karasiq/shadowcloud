package com.karasiq.shadowcloud.exceptions

import java.io.IOException
import java.util.UUID

sealed abstract class CryptoException(message: String = null, cause: Throwable = null) extends IOException(message, cause)

object CryptoException {
  case class EncryptError(cause: Throwable = null) extends CryptoException("Encryption error", cause)
  case class DecryptError(cause: Throwable = null) extends CryptoException("Decryption error", cause)
  case class InvalidSignature(cause: Throwable = null) extends CryptoException("Invalid signature", cause)
  case class KeyMissing(keyId: UUID, cause: Throwable = null) extends CryptoException("Key missing: " + keyId, cause)
}