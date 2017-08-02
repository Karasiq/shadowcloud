package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.internal.{NoOpEncryptionModule, NoOpHashingModule, NoOpSignModule}
import com.karasiq.shadowcloud.providers.CryptoProvider

private[crypto] final class NoOpCryptoProvider(config: Config) extends CryptoProvider {
  private[this] object settings extends ConfigImplicits {
    val requireEncryption: Boolean = config.withDefault(true, _.getBoolean("crypto.no-op.require-encryption"))
  }

  override def hashing: HashingPF = {
    case m if m.algorithm.isEmpty ⇒ new NoOpHashingModule
  }

  override def encryption: EncryptionPF = {
    case m if m.algorithm.isEmpty ⇒
      require(!settings.requireEncryption, "Valid encryption is required")
      new NoOpEncryptionModule
  }

  override def signing: SignPF = {
    case m if m.algorithm.isEmpty ⇒
      require(!settings.requireEncryption, "Valid signature is required")
      new NoOpSignModule
  }
}
