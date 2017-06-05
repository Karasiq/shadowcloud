package com.karasiq.shadowcloud.config.keys

import java.util.UUID

trait KeyProvider {
  def forEncryption(): KeySet
  def forId(keyId: UUID): KeySet
}
