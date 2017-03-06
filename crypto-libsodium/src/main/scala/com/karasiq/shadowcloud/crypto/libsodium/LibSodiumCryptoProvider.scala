package com.karasiq.shadowcloud.crypto.libsodium

import com.karasiq.shadowcloud.crypto.libsodium.internal._
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, HashingMethod}
import com.karasiq.shadowcloud.providers.CryptoProvider

import scala.language.postfixOps

final class LibSodiumCryptoProvider extends CryptoProvider {
  override val hashingAlgorithms: Set[String] = ifLoaded(super.hashingAlgorithms) {
    Set("SHA256", "SHA512", "BLAKE2")
  }

  // TODO: Streaming hashers
  override def hashing: HashingPF = ifLoaded(super.hashing) {
    case method @ HashingMethod("SHA256", false, _, _) ⇒
      new SHA256HashingModule(method)

    case method @ HashingMethod("SHA512", false, _, _) ⇒
      new SHA512HashingModule(method)

    case method @ HashingMethod("BLAKE2", false, _, _) ⇒
      new BLAKE2HashingModule(method)
  }

  // TODO: AES
  override def encryptionAlgorithms: Set[String] = ifLoaded(super.encryptionAlgorithms) {
    Set("Salsa20")
  }

  override def encryption: EncryptionPF = ifLoaded(super.encryption) {
    case method @ EncryptionMethod("Salsa20", 256, _, _, _) ⇒
      new SalsaEncryptionModule(method)
  }

  @inline
  private[this] def ifLoaded[T](empty: ⇒ T)(value: ⇒ T): T = {
    if (LSUtils.libraryLoaded) value else empty
  }
}
