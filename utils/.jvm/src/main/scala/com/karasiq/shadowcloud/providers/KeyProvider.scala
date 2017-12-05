package com.karasiq.shadowcloud.providers

import scala.concurrent.Future

import akka.Done

import com.karasiq.shadowcloud.model.keys.{KeyChain, KeyId, KeySet}
import com.karasiq.shadowcloud.model.keys.KeyProps.RegionSet

trait KeyProvider {
  def addKeySet(key: KeySet, regionSet: RegionSet = RegionSet.all, forEncryption: Boolean = true, forDecryption: Boolean = true): Future[KeySet]
  def modifyKeySet(keyId: KeyId, regionSet: RegionSet = RegionSet.all, forEncryption: Boolean = true, forDecryption: Boolean = true): Future[Done]
  def getKeyChain(): Future[KeyChain]
}