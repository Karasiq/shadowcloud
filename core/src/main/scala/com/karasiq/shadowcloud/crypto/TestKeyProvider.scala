package com.karasiq.shadowcloud.crypto

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.config.keys.{KeyChain, KeyProvider, KeySet}

private[crypto] final class TestKeyProvider(sc: ShadowCloudExtension) extends KeyProvider {
  private[this] val keys = TrieMap.empty[KeySet.ID, (KeySet, Boolean, Boolean)]

  def addKeySet(key: KeySet, forEncryption: Boolean, forDecryption: Boolean): Future[KeySet] = {
    val result = keys.putIfAbsent(key.id, (key, forEncryption, forDecryption))
    if (result.isEmpty) Future.successful(key) else Future.failed(new IllegalArgumentException("Key already exists"))
  }

  def getKeyChain(): Future[KeyChain] = {
    if (keys.isEmpty) {
      addKeySet(sc.keys.generateKeySet())
    }
    val vector = keys.values.toVector
    Future.successful(KeyChain(vector.filter(_._2).map(_._1), vector.filter(_._3).map(_._1)))
  }
}
