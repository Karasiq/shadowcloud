package com.karasiq.shadowcloud.config.keys

import java.util.UUID

import com.karasiq.shadowcloud.crypto.{EncryptionParameters, SignParameters}

case class KeySet(id: KeySet.ID, signing: SignParameters, encryption: EncryptionParameters)

object KeySet {
  type ID = UUID
  // val empty = KeySet(new UUID(0, 0), SignParameters.empty, EncryptionParameters.empty)
}