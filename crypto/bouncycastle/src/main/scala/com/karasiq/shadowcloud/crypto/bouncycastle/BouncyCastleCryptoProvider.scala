package com.karasiq.shadowcloud.crypto.bouncycastle

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric.{ECIESCipherModule, RSACipherModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.{BCDigestModule, BCDigests}
import com.karasiq.shadowcloud.crypto.bouncycastle.sign.{ECDSASignModule, RSASignModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.symmetric._
import com.karasiq.shadowcloud.providers.CryptoProvider

final class BouncyCastleCryptoProvider extends CryptoProvider with ConfigImplicits {
  override val hashingAlgorithms: Set[String] = {
    BCDigests.algorithms
  }

  override def hashing: HashingPF = {
    case method if BCDigests.isDigestAlgorithm(method.algorithm) ⇒
      BCDigestModule(method)
  }

  override val encryptionAlgorithms: Set[String] = {
    BCBlockCiphers.blockAlgorithms ++
      BCBlockCiphers.aeadAlgorithms ++
      BCStreamCiphers.algorithms ++
      Set("ECIES", "RSA")
  }

  // TODO: AESFastEngine, Poly1305
  override def encryption: EncryptionPF = {
    case method if BCBlockCiphers.isAEADAlgorithm(method.algorithm) ⇒
      AEADBlockCipherModule(method)

    case method if BCBlockCiphers.isBlockAlgorithm(method.algorithm) ⇒
      BlockCipherModule(method)

    case method if BCStreamCiphers.isStreamAlgorithm(method.algorithm) ⇒
      StreamCipherModule(method)

    case method if method.algorithm == "ECIES" ⇒
      ECIESCipherModule(method)

    case method if method.algorithm == "RSA" && method.keySize >= 1024 ⇒
      RSACipherModule(method)
  }

  override def signingAlgorithms: Set[String] = {
    Set("ECDSA", "RSA")
  }

  override def signing: SignPF = {
    case method if method.algorithm == "ECDSA" ⇒
      ECDSASignModule(method)

    case method if method.algorithm == "RSA" && method.keySize >= 1024 ⇒
      RSASignModule(method)
  }
}
