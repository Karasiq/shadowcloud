package com.karasiq.shadowcloud.crypto

import com.karasiq.shadowcloud.crypto.EncryptionMethod.Plain
import com.karasiq.shadowcloud.crypto.internal.{NoOpHashingModule, PlainEncryptionModule}
import com.karasiq.shadowcloud.providers.ModuleProvider

import scala.language.postfixOps

final class DefaultCryptoProvider extends ModuleProvider {
  override def hashing: PartialFunction[HashingMethod, HashingModule] = {
    case HashingMethod.NoHashing ⇒
      new NoOpHashingModule
  }

  override def encryption: PartialFunction[EncryptionMethod, EncryptionModule] = {
    case Plain ⇒
      new PlainEncryptionModule
  }
}
