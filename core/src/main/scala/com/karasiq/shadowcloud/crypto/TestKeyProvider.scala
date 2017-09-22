package com.karasiq.shadowcloud.crypto

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

import akka.Done

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.model.keys.{KeyChain, KeyId, KeySet}
import com.karasiq.shadowcloud.providers.KeyProvider

private[crypto] final class TestKeyProvider(sc: ShadowCloudExtension) extends KeyProvider {
  private[this] case class KeySetContainer(keySet: KeySet, forEncryption: Boolean, forDecryption: Boolean)
  private[this] val keys = TrieMap.empty[KeyId, KeySetContainer]

  def addKeySet(key: KeySet, forEncryption: Boolean, forDecryption: Boolean): Future[KeySet] = {
    val result = keys.putIfAbsent(key.id, KeySetContainer(key, forEncryption, forDecryption))
    if (result.isEmpty) Future.successful(key) else Future.failed(new IllegalArgumentException("Key already exists"))
  }

  def modifyKeySet(keyId: KeyId, forEncryption: Boolean, forDecryption: Boolean) = {
    keys.get(keyId) match {
      case Some(key) ⇒
        keys += keyId → key.copy(key.keySet, forEncryption, forDecryption)
        Future.successful(Done)

      case None ⇒
        Future.failed(new NoSuchElementException(keyId.toString))
    }
  }

  def getKeyChain(): Future[KeyChain] = {
    if (keys.isEmpty) {
      addKeySet(sc.keys.generateKeySet())
    }
    val vector = keys.values.toVector

    Future.successful(KeyChain(
      vector.filter(_.forEncryption).map(_.keySet),
      vector.filter(_.forDecryption).map(_.keySet)
    ))
  }
}
