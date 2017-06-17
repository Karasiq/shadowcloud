package com.karasiq.shadowcloud.config.keys

import scala.concurrent.Future

trait KeyManager extends KeyProvider {
  def createKey(forEncryption: Boolean = true, forDecryption: Boolean = true): Future[KeySet]
}
