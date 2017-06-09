package com.karasiq.shadowcloud.persistence.h2

import java.util.UUID

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.config.keys.{KeyProvider, KeySet}

class H2KeyProvider(sc: ShadowCloudExtension) extends KeyProvider {
  val h2 = H2DB(sc.implicits.actorSystem)
  val keyManager = new H2KeyManager(h2)

  def forEncryption(): KeySet = keyManager.forEncryption()
  def forDecryption(keyId: UUID): KeySet = keyManager.forDecryption(keyId)
}
