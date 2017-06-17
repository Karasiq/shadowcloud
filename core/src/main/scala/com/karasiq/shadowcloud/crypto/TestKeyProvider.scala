package com.karasiq.shadowcloud.crypto

import java.util.UUID

import scala.concurrent.Future

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.config.keys.{KeyProvider, KeySet}
import com.karasiq.shadowcloud.exceptions.CryptoException

private[crypto] final class TestKeyProvider(sc: ShadowCloudExtension) extends KeyProvider {
  private[this] lazy val keySet = sc.keys.generateKeySet()

  def forEncryption(): Future[KeySet] = {
    Future.successful(keySet)
  }

  def forDecryption(keyId: UUID): Future[KeySet] = {
    if (keyId == keySet.id) {
      Future.successful(keySet)
    } else {
      Future.failed(CryptoException.KeyMissing(keyId))
    }
  }
}
