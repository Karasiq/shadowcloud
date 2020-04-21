package com.karasiq.shadowcloud.crypto



import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.crypto.internal.{NoOpEncryptionModule, NoOpHashingModule, NoOpSignModule}
import com.karasiq.shadowcloud.model.crypto.CryptoMethod
import com.karasiq.shadowcloud.providers.CryptoProvider
import com.typesafe.config.Config

private[crypto] final class NoOpCryptoProvider(config: Config) extends CryptoProvider {
  private[this] object settings extends ConfigImplicits {
    val requireEncryption: Boolean = config.withDefault(true, _.getBoolean("crypto.no-op.require-encryption"))
  }

  override def hashing: HashingPF = {
    case m if CryptoMethod.isNoOpMethod(m) ⇒
      new NoOpHashingModule
  }

  override def encryption: EncryptionPF = {
    case m if CryptoMethod.isNoOpMethod(m) ⇒
      require(!settings.requireEncryption, "Valid encryption is required")
      new NoOpEncryptionModule
  }

  override def signing: SignPF = {
    case m if CryptoMethod.isNoOpMethod(m) ⇒
      require(!settings.requireEncryption, "Valid signature is required")
      new NoOpSignModule
  }
}
