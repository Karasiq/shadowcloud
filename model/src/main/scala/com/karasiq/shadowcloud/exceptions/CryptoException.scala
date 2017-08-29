package com.karasiq.shadowcloud.exceptions

import akka.util.ByteString

import com.karasiq.shadowcloud.utils.HexString

sealed abstract class CryptoException(message: String = null, cause: Throwable = null) extends SCException(message, cause)

object CryptoException {
  final case class ChecksumError(expected: ByteString, actual: ByteString) extends CryptoException(
    "Checksum not match: " + HexString.encode(expected) + " / " + HexString.encode(actual))
  final case class EncryptError(cause: Throwable = null) extends CryptoException("Encryption error", cause)
  final case class DecryptError(cause: Throwable = null) extends CryptoException("Decryption error", cause)
  final case class InvalidSignature(cause: Throwable = null) extends CryptoException("Invalid signature", cause)
  final case class KeyMissing(cause: Throwable = null) extends CryptoException("No appropriate key found", cause)
  final case class ReuseError(cause: Throwable = null) extends CryptoException("Crypto parameters illegal reuse")
}