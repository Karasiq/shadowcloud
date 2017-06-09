package com.karasiq.shadowcloud.persistence

import java.util.UUID

import com.karasiq.shadowcloud.config.keys.{KeyChain, KeyProvider, KeySet}

trait KeyManager extends KeyProvider {
  def createKey(forEncryption: Boolean = true, forDecryption: Boolean = true): KeySet
  def keyChain(): KeyChain

  def forEncryption(): KeySet = {
    val keys = keyChain().encKeys.values
    if (keys.isEmpty) createKey() else keys.head
  }

  def forDecryption(keyId: UUID): KeySet = {
    keyChain().decKeys(keyId)
  }
}
