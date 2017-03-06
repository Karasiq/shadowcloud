package com.karasiq.shadowcloud.crypto.libsodium

import com.karasiq.shadowcloud.crypto.libsodium.internal._
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, HashingMethod}
import com.karasiq.shadowcloud.providers.CryptoProvider

import scala.language.postfixOps

final class LibSodiumCryptoProvider extends CryptoProvider {
  override val hashingAlgorithms: Set[String] = ifLoaded(super.hashingAlgorithms) {
    Set("SHA256", "SHA512", "BLAKE2")
  }

  override def hashing: HashingPF = ifLoaded(super.hashing) {
    case method @ HashingMethod("SHA256", _, _, _) ⇒
      new MultiPartHashingModule(method, _.sha256())

    case method @ HashingMethod("SHA512", _, _, _) ⇒
      new MultiPartHashingModule(method, _.sha512())

    case method @ HashingMethod("BLAKE2" | "Blake2b", _, _, _) ⇒
      new BLAKE2HashingModule(method)
  }

  // TODO: AES
  override def encryptionAlgorithms: Set[String] = ifLoaded(super.encryptionAlgorithms) {
    Set("XSalsa20/Poly1305", "ChaCha20/Poly1305") ++ (if (LSUtils.aes256GcmAvailable) Set("AES/GCM") else Set.empty)
  }

  override def encryption: EncryptionPF = ifLoaded(super.encryption) {
    case method @ EncryptionMethod("XSalsa20/Poly1305" | "XSalsa20" | "Salsa20", 256, _, _, _) ⇒
      new SecretBoxEncryptionModule(method)

    case method @ EncryptionMethod("ChaCha20/Poly1305" | "ChaCha20", 256, _, _, _)  ⇒
      new AEADEncryptionModule(false, method)

    case method @ EncryptionMethod("AES/GCM" | "AES", 256, _, _, _) if LSUtils.aes256GcmAvailable ⇒
      new AEADEncryptionModule(true, method)
  }

  @inline
  private[this] def ifLoaded[T](empty: ⇒ T)(value: ⇒ T): T = {
    if (LSUtils.libraryAvailable) value else empty
  }
}
