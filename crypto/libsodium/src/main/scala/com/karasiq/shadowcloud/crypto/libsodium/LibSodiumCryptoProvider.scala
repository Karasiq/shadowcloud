package com.karasiq.shadowcloud.crypto.libsodium

import com.karasiq.shadowcloud.crypto.libsodium.asymmetric.SealedBoxModule
import com.karasiq.shadowcloud.crypto.libsodium.hashing.{Blake2bModule, MultiPartHashModule}
import com.karasiq.shadowcloud.crypto.libsodium.internal.LSUtils
import com.karasiq.shadowcloud.crypto.libsodium.signing.CryptoSignModule
import com.karasiq.shadowcloud.crypto.libsodium.symmetric._
import com.karasiq.shadowcloud.providers.CryptoProvider

final class LibSodiumCryptoProvider extends CryptoProvider {
  private[this] val libraryLoaded = LSUtils.isLibraryAvailable
  private[this] val aesAvailable  = LSUtils.isAesAvailable

  override val hashingAlgorithms: Set[String] = ifLoaded(super.hashingAlgorithms) {
    Set("SHA256", "SHA512", "Blake2b")
  }

  override def hashing: HashingPF = ifLoaded(super.hashing) {
    case method if method.algorithm == "Blake2b" ⇒
      Blake2bModule(method)

    case method if method.algorithm == "SHA256" ⇒
      MultiPartHashModule.SHA256(method)

    case method if method.algorithm == "SHA512" ⇒
      MultiPartHashModule.SHA512(method)
  }

  override def encryptionAlgorithms: Set[String] = ifLoaded(super.encryptionAlgorithms) {
    @inline def onlyIf(cond: Boolean)(algorithms: String*): Seq[String] = if (cond) algorithms else Nil

    Set("ChaCha20/Poly1305", "Salsa20", "XSalsa20", "ChaCha20", SecretBoxModule.Algorithm, SealedBoxModule.Algorithm) ++
      onlyIf(aesAvailable)("AES/GCM")
  }

  override def encryption: EncryptionPF = ifLoaded(super.encryption) {
    case method if method.algorithm == SealedBoxModule.Algorithm ⇒
      SealedBoxModule(method)

    case method if method.algorithm == SecretBoxModule.Algorithm ⇒
      SecretBoxModule(method)

    case method if method.algorithm == "ChaCha20/Poly1305" || (method.algorithm == "AES/GCM" && method.keySize == 256 && aesAvailable) ⇒
      AEADCipherModule(method)

    case method if method.algorithm == "Salsa20" ⇒
      Salsa20Module(method)

    case method if method.algorithm == "XSalsa20" ⇒
      XSalsa20Module(method)

    case method if method.algorithm == "ChaCha20" ⇒
      ChaCha20Module(method)
  }

  override def signingAlgorithms = ifLoaded(super.signingAlgorithms) {
    Set(CryptoSignModule.Algorithm)
  }

  override def signing = ifLoaded(super.signing) {
    case method if method.algorithm == CryptoSignModule.Algorithm ⇒
      CryptoSignModule(method)
  }

  @inline
  private[this] def ifLoaded[T](empty: ⇒ T)(value: ⇒ T): T = {
    if (libraryLoaded) value else empty
  }
}
