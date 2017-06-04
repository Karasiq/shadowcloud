package com.karasiq.shadowcloud.config.keys

import java.util.UUID

import com.karasiq.shadowcloud.crypto.{EncryptionParameters, SignParameters}

case class KeySet(id: UUID, sign: SignParameters, encryption: EncryptionParameters)

object KeySet {
  val empty = KeySet(new UUID(0, 0), SignParameters.empty, EncryptionParameters.empty)
}