package com.karasiq.shadowcloud.config.keys

import java.util.UUID

trait KeyProvider {
  def forEncryption(): KeySet
  def forDecryption(keyId: UUID): KeySet
}
