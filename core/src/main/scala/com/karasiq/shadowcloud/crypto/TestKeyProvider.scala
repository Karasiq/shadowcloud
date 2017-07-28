package com.karasiq.shadowcloud.crypto

import scala.concurrent.Future

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.config.keys.{KeyChain, KeyProvider}

private[crypto] final class TestKeyProvider(sc: ShadowCloudExtension) extends KeyProvider {
  private[this] lazy val keySet = sc.keys.generateKeySet()

  def getKeyChain(): Future[KeyChain] = {
    val keyMap = Map(keySet.id → keySet)
    Future.successful(KeyChain(keyMap, keyMap))
  }
}
