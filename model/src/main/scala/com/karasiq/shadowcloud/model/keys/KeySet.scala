package com.karasiq.shadowcloud.model.keys

import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutKeys}
import com.karasiq.shadowcloud.model.SCEntity
import com.karasiq.shadowcloud.model.crypto.{EncryptionParameters, SignParameters}

@SerialVersionUID(0L)
final case class KeySet(id: KeyId, encryption: EncryptionParameters, signing: SignParameters)
  extends SCEntity with HasEmpty with HasWithoutKeys {

  type Repr = KeySet
  
  def isEmpty: Boolean = encryption.isEmpty && encryption.isEmpty
  def withoutKeys = copy(signing = signing.withoutKeys, encryption = encryption.withoutKeys)
}

object KeySet {
 val empty = KeySet(KeyId.empty, EncryptionParameters.empty, SignParameters.empty)
}