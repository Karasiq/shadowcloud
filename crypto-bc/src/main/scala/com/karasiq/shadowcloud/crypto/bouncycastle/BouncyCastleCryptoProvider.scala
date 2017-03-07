package com.karasiq.shadowcloud.crypto.bouncycastle

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.{BCDigestModule, JavaMessageDigestModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal._
import com.karasiq.shadowcloud.crypto.bouncycastle.symmetric.{AEADBlockCipherModule, StreamCipherModule}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, HashingMethod}
import com.karasiq.shadowcloud.providers.CryptoProvider

import scala.language.postfixOps

final class BouncyCastleCryptoProvider extends CryptoProvider with ConfigImplicits {
  override val hashingAlgorithms: Set[String] = {
    BCUtils.DIGESTS.toSet
  }

  override def hashing: HashingPF = {
    case method @ HashingMethod("Blake2b" | "BLAKE2", _, _, _) ⇒
      BCDigestModule.Blake2b(method)

    case method if hashingAlgorithms.contains(method.algorithm) ⇒
      JavaMessageDigestModule(method)
  }

  override val encryptionAlgorithms: Set[String] = {
    Set("AES/GCM", "Salsa20", "XSalsa20", "ChaCha20")
  }

  // TODO: AESFastEngine, Poly1305
  override def encryption: EncryptionPF = {
    case method @ EncryptionMethod("AES/GCM", 128 | 256, _, _, _) ⇒
      AEADBlockCipherModule.AES_GCM(method)

    case method @ EncryptionMethod("Salsa20", 128 | 256, _, _, _) ⇒
      StreamCipherModule.Salsa20(method)

    case method @ EncryptionMethod("XSalsa20", 128 | 256, _, _, _) ⇒
      StreamCipherModule.XSalsa20(method)

    case method @ EncryptionMethod("ChaCha20", 128 | 256, _, _, _) ⇒
      StreamCipherModule.ChaCha20(method)
  }
}
