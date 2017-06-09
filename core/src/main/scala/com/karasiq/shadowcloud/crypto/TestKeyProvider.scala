package com.karasiq.shadowcloud.crypto

import java.util.UUID

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.config.keys.{KeyProvider, KeySet}

private[crypto] final class TestKeyProvider(sc: ShadowCloudExtension) extends KeyProvider {
  private[this] lazy val keySet = sc.keys.generateKeySet()

  def forEncryption(): KeySet = {
    keySet
  }

  def forDecryption(keyId: UUID): KeySet = {
    if (keyId == keySet.id) keySet else throw new NoSuchElementException("Unknown key: " + keyId)
  }
}
