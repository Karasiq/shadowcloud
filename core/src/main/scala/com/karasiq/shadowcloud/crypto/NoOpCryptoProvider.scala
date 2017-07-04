package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

import com.karasiq.shadowcloud.crypto.internal.{NoOpEncryptionModule, NoOpHashingModule, NoOpSignModule}
import com.karasiq.shadowcloud.providers.CryptoProvider

private[crypto] final class NoOpCryptoProvider extends CryptoProvider {
  override def hashing: HashingPF = {
    case m if m.algorithm.isEmpty ⇒ new NoOpHashingModule
  }

  override def encryption: EncryptionPF = {
    case m if m.algorithm.isEmpty ⇒ new NoOpEncryptionModule
  }

  override def signing: SignPF = {
    case m if m.algorithm.isEmpty ⇒ new NoOpSignModule
  }
}
