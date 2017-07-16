package com.karasiq.shadowcloud.crypto.bouncycastle

import scala.language.postfixOps

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.EncryptionMethod
import com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric.{ECIESCipherModule, RSACipherModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.{BCDigests, MessageDigestModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.sign.{ECDSASignModule, RSASignModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.symmetric.{AEADBlockCipherModule, BCBlockCiphers, BCStreamCiphers, StreamCipherModule}
import com.karasiq.shadowcloud.providers.CryptoProvider

final class BouncyCastleCryptoProvider extends CryptoProvider with ConfigImplicits {
  override val hashingAlgorithms: Set[String] = {
    BCDigests.algorithms.toSet
  }

  override def hashing: HashingPF = {
    case method if hashingAlgorithms.contains(method.algorithm) ⇒
      MessageDigestModule(method)
  }

  override val encryptionAlgorithms: Set[String] = {
    Set("AES/GCM", "Salsa20", "XSalsa20", "ChaCha20", "ECIES", "RSA")
  }

  // TODO: AESFastEngine, Poly1305, Non-AEAD block ciphers
  override def encryption: EncryptionPF = {
    case method @ EncryptionMethod(algorithm, 128 | 256, _, _, _) if BCBlockCiphers.isAEADAlgorithm(algorithm) ⇒
      AEADBlockCipherModule(method)

    case method @ EncryptionMethod(algorithm, 128 | 256, _, _, _) if BCStreamCiphers.isStreamAlgorithm(algorithm) ⇒
      StreamCipherModule(method)

    case method if method.algorithm == "ECIES" ⇒
      ECIESCipherModule(method)

    case method if method.algorithm == "RSA" ⇒
      RSACipherModule(method)
  }

  override def signingAlgorithms: Set[String] = {
    Set("RSA", "ECDSA")
  }

  override def signing: SignPF = {
    case method if method.algorithm == "ECDSA" ⇒
      ECDSASignModule(method)

    case method if method.algorithm == "RSA" ⇒
      RSASignModule(method)
  }
}
