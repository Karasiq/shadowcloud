package com.karasiq.shadowcloud.crypto

import java.util.UUID

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.config.keys.{KeyProvider, KeySet}

private[crypto] final class TestKeyProvider(sc: ShadowCloudExtension) extends KeyProvider {
  private[this] lazy val keySet = {
    val enc = sc.modules.encryptionModule(sc.config.crypto.encryption.keys).createParameters()
    val sign = sc.modules.signModule(sc.config.crypto.signing.index).createParameters()
    KeySet(UUID.randomUUID(), sign, enc)
  }

  def forEncryption(): KeySet = {
    keySet
  }

  def forId(keyId: UUID): KeySet = {
    if (keyId == keySet.id) keySet else throw new NoSuchElementException("Unknown key: " + keyId)
  }
}
