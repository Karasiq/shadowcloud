package com.karasiq.shadowcloud.persistence.h2

import java.util.UUID

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.config.keys.{KeyChain, KeySet}
import com.karasiq.shadowcloud.persistence.KeyManager
import com.karasiq.shadowcloud.persistence.utils.SCQuillEncoders

final class H2KeyManager(h2: H2DBExtension) extends KeyManager {
  import h2.sc
  import h2.context.db
  import db._

  private[this] object schema extends SCQuillEncoders {
    case class DBKey(id: UUID, forEncryption: Boolean, forDecryption: Boolean, key: ByteString)

    implicit val keySchemaMeta = schemaMeta[DBKey]("sc_keys", _.id → "key_id",
      _.forEncryption → "for_encryption", _.forDecryption → "for_decryption",
      _.key → "serialized_key")
  }

  import schema._

  //noinspection TypeAnnotation
  private[this] object queries {
    def addKey(key: DBKey) = quote {
      query[DBKey].insert(lift(key))
    }

    val getKeys = quote {
      query[DBKey]
    }
  }

  def createKey(forEncryption: Boolean, forDecryption: Boolean): KeySet = {
    def writeKey(keySet: KeySet): DBKey = DBKey(keySet.id, forEncryption, forDecryption, sc.serialization.toBytes(keySet))
    val keySet = sc.keys.generateKeySet()
    db.run(queries.addKey(writeKey(keySet)))
    keySet
  }

  def keyChain(): KeyChain = {
    def readKey(bs: ByteString): KeySet = sc.serialization.fromBytes[KeySet](bs)
    def toMap(keys: List[DBKey]): Map[UUID, KeySet] = keys.map(k ⇒ (k.id, readKey(k.key))).toMap
    val keys = db.run(queries.getKeys)
    KeyChain(toMap(keys.filter(_.forEncryption)), toMap(keys.filter(_.forDecryption)))
  }
}
