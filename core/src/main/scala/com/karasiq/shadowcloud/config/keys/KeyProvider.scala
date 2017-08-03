package com.karasiq.shadowcloud.config.keys

import scala.concurrent.Future

trait KeyProvider {
  def addKeySet(key: KeySet, forEncryption: Boolean = true, forDecryption: Boolean = true): Future[KeySet]
  def getKeyChain(): Future[KeyChain]
}
