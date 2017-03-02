package com.karasiq.shadowcloud.crypto

import com.karasiq.shadowcloud.crypto.EncryptionMethod.{AES, Plain}
import com.karasiq.shadowcloud.providers.ModuleProvider

import scala.language.postfixOps

final class DefaultCryptoProvider extends ModuleProvider {
  override def hashing: PartialFunction[HashingMethod, HashingModule] = {
    case HashingMethod.NoHashing ⇒
      HashingModule.none

    case HashingMethod.Digest(alg) ⇒
      HashingModule.digest(alg)
  }

  override def encryption: PartialFunction[EncryptionMethod, EncryptionModule] = {
    case Plain ⇒
      EncryptionModule.plain

    case AES("GCM", bits @ (128 | 256)) ⇒
      EncryptionModule.AES_GCM(bits)
  }
}
