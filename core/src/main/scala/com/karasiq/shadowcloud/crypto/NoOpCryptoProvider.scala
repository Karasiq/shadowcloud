package com.karasiq.shadowcloud.crypto

import com.karasiq.shadowcloud.crypto.internal.{NoOpEncryptionModule, NoOpHashingModule}
import com.karasiq.shadowcloud.providers.ModuleProvider

import scala.language.postfixOps

private[crypto] final class NoOpCryptoProvider extends ModuleProvider {
  override val name = "no-op"

  override def hashing: HashingPF = {
    case m if m.algorithm.isEmpty ⇒
      new NoOpHashingModule
  }

  override def encryption: EncryptionPF = {
    case m if m.algorithm.isEmpty ⇒
      new NoOpEncryptionModule
  }
}
