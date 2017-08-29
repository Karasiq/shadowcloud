package com.karasiq.shadowcloud.model.keys

import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model.SCEntity
import com.karasiq.shadowcloud.model.crypto.{EncryptionParameters, SignParameters}

@SerialVersionUID(0L)
final case class KeySet(id: KeyId, signing: SignParameters, encryption: EncryptionParameters) extends SCEntity with HasEmpty {
  def isEmpty: Boolean = encryption.isEmpty && encryption.isEmpty
}

object KeySet {
 val empty = KeySet(KeyId.empty, SignParameters.empty, EncryptionParameters.empty)
}