package com.karasiq.shadowcloud.crypto.bouncycastle

import java.security.MessageDigest

import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{AESGCMEncryptionModule, BCUtils, MessageDigestHashingModule}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule, HashingMethod, HashingModule}
import com.karasiq.shadowcloud.providers.ModuleProvider

import scala.language.postfixOps

final class BouncyCastleCryptoProvider extends ModuleProvider {
  override def hashing: PartialFunction[HashingMethod, HashingModule] = {
    case HashingMethod.Digest(alg) ⇒
      val messageDigest = MessageDigest.getInstance(alg, BCUtils.provider)
      new MessageDigestHashingModule(messageDigest)
  }

  override def encryption: PartialFunction[EncryptionMethod, EncryptionModule] = {
    case EncryptionMethod.AES("GCM", bits @ (128 | 256)) ⇒
      new AESGCMEncryptionModule(bits)
  }
}
