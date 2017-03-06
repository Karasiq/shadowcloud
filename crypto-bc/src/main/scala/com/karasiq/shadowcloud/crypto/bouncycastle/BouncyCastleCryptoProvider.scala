package com.karasiq.shadowcloud.crypto.bouncycastle

import java.security.MessageDigest

import com.karasiq.shadowcloud.crypto.EncryptionMethod
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{AEADBlockCipherEncryptionModule, BCUtils, MessageDigestHashingModule}
import com.karasiq.shadowcloud.providers.CryptoProvider

import scala.language.postfixOps

final class BouncyCastleCryptoProvider extends CryptoProvider {
  override val hashingAlgorithms: Set[String] = {
    BCUtils.DIGESTS.toSet
  }

  override def hashing: HashingPF = {
    case method if hashingAlgorithms.contains(method.algorithm) ⇒
      val messageDigest = MessageDigest.getInstance(method.algorithm, BCUtils.provider)
      new MessageDigestHashingModule(method, messageDigest)
  }

  override val encryptionAlgorithms: Set[String] = {
    Set("AES", "AES/GCM")
  }

  // TODO: AESFastEngine, Salsa20
  override def encryption: EncryptionPF = {
    case method @ EncryptionMethod("AES" | "AES/GCM", 128 | 256, _, _, _) ⇒
      AEADBlockCipherEncryptionModule.AES_GCM(method)
  }
}
