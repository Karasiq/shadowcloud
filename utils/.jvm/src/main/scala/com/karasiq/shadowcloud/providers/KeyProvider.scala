package com.karasiq.shadowcloud.providers

import scala.concurrent.Future

import com.karasiq.shadowcloud.config.keys.{KeyChain, KeySet}

trait KeyProvider {
  def addKeySet(key: KeySet, forEncryption: Boolean = true, forDecryption: Boolean = true): Future[KeySet]
  def getKeyChain(): Future[KeyChain]
}
