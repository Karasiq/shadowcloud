package com.karasiq.shadowcloud.providers

import scala.concurrent.Future

import akka.Done

import com.karasiq.shadowcloud.model.keys.{KeyChain, KeyId, KeySet}

trait KeyProvider {
  def addKeySet(key: KeySet, forEncryption: Boolean = true, forDecryption: Boolean = true): Future[KeySet]
  def modifyKeySet(keyId: KeyId, forEncryption: Boolean, forDecryption: Boolean = true): Future[Done]
  def getKeyChain(): Future[KeyChain]
}
