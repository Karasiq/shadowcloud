package com.karasiq.shadowcloud.crypto

import com.karasiq.shadowcloud.crypto.internal.{NoOpEncryptionModule, NoOpHashingModule}
import com.karasiq.shadowcloud.providers.CryptoProvider

import scala.language.postfixOps

private[crypto] final class NoOpCryptoProvider extends CryptoProvider {
  override def hashing: HashingPF = {
    case m if m.algorithm.isEmpty ⇒ new NoOpHashingModule
  }

  override def encryption: EncryptionPF = {
    case m if m.algorithm.isEmpty ⇒ new NoOpEncryptionModule
  }
}
