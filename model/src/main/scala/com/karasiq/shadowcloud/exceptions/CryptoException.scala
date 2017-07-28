package com.karasiq.shadowcloud.exceptions

import java.io.IOException

sealed abstract class CryptoException(message: String = null, cause: Throwable = null) extends IOException(message, cause)

object CryptoException {
  case class EncryptError(cause: Throwable = null) extends CryptoException("Encryption error", cause)
  case class DecryptError(cause: Throwable = null) extends CryptoException("Decryption error", cause)
  case class InvalidSignature(cause: Throwable = null) extends CryptoException("Invalid signature", cause)
  case class KeyMissing(cause: Throwable = null) extends CryptoException("No appropriate key found", cause)
}