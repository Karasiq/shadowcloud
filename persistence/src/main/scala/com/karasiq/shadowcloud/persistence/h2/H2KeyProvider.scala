package com.karasiq.shadowcloud.persistence.h2

import java.util.UUID

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

import akka.actor.ActorSystem
import akka.util.ByteString

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.config.keys.{KeyChain, KeyManager, KeySet}
import com.karasiq.shadowcloud.persistence.utils.SCQuillEncoders

final class H2KeyProvider(actorSystem: ActorSystem) extends KeyManager {
  private[this] val h2 = H2DB(actorSystem)
  private[this] val sc = ShadowCloud(actorSystem)

  import h2.context
  import context._
  import h2.settings.executionContext

  // -----------------------------------------------------------------------
  // Schema
  // -----------------------------------------------------------------------
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

    /* val getEncKey = quote {
      query[DBKey].filter(_.forEncryption).take(1)
    }

    def getDecKey(id: UUID) = quote {
      query[DBKey].filter(k ⇒ k.id == lift(id) && k.forDecryption).take(1)
    } */

    val getKeys = quote {
      query[DBKey]
    }
  }

  // -----------------------------------------------------------------------
  // Conversions
  // -----------------------------------------------------------------------
  private[this] object conversions {
    def toDBKey(keySet: KeySet, forEncryption: Boolean, forDecryption: Boolean): DBKey = {
      DBKey(keySet.id, forEncryption, forDecryption, sc.serialization.toBytes(keySet))
    }

    def toKeySet(key: DBKey): KeySet = {
      sc.serialization.fromBytes[KeySet](key.key)
    }
  }

  // -----------------------------------------------------------------------
  // Key manager functions
  // -----------------------------------------------------------------------
  def createKey(forEncryption: Boolean, forDecryption: Boolean): Future[KeySet] = {
    Future.fromTry(Try {
      val keySet = sc.keys.generateKeySet()
      context.run(queries.addKey(conversions.toDBKey(keySet, forEncryption, forDecryption)))
      keySet
    })
  }

  /* def forEncryption(): Future[KeySet] = {
    Future(db.run(queries.getEncKey).head)
      .map(conversions.toKeySet)
      .recoverWith { case _: NoSuchElementException ⇒ createKey() }
  }

  def forDecryption(keyId: UUID): Future[KeySet] = {
    Future(db.run(queries.getDecKey(keyId)).head)
      .map(conversions.toKeySet)
      .recoverWith { case exc ⇒ Future.failed(CryptoException.KeyMissing(exc)) }
  } */

  override def getKeyChain(): Future[KeyChain] = {
    def readKey(bs: ByteString): KeySet = sc.serialization.fromBytes[KeySet](bs)
    def toMap(keys: List[DBKey]): Map[UUID, KeySet] = keys.map(k ⇒ (k.id, readKey(k.key))).toMap

    val future = Future.fromTry(Try {
      val keys = context.run(queries.getKeys)
      KeyChain(toMap(keys.filter(_.forEncryption)), toMap(keys.filter(_.forDecryption)))
    })

    future.flatMap { keyChain ⇒
      if (keyChain.encKeys.isEmpty) createKey().map(ks ⇒ keyChain.copy(encKeys = keyChain.encKeys + (ks.id → ks)))
      else Future.successful(keyChain)
    }
  }
}
