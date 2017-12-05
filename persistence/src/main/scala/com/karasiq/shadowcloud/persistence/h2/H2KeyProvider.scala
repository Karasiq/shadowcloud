package com.karasiq.shadowcloud.persistence.h2

import java.util.UUID

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

import akka.Done
import akka.actor.ActorSystem
import akka.util.ByteString

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.model.keys.{KeyChain, KeyId, KeyProps, KeySet}
import com.karasiq.shadowcloud.model.keys.KeyProps.RegionSet
import com.karasiq.shadowcloud.persistence.utils.SCQuillEncoders
import com.karasiq.shadowcloud.providers.KeyProvider

final class H2KeyProvider(actorSystem: ActorSystem) extends KeyProvider {
  private[this] val h2 = H2DB(actorSystem)
  private[this] val sc = ShadowCloud(actorSystem)

  import h2.context
  import context._

  // -----------------------------------------------------------------------
  // Schema
  // -----------------------------------------------------------------------
  private[this] object schema extends SCQuillEncoders {
    @SerialVersionUID(0L)
    final case class DBKey(id: UUID, regionSet: RegionSet, forEncryption: Boolean, forDecryption: Boolean, key: ByteString)

    implicit val regionsEncoder: Encoder[RegionSet] = encoder(java.sql.Types.ARRAY, (index, value, row) ⇒
      row.setObject(index, value.toArray, java.sql.Types.ARRAY))

    implicit val regionsDecoder: Decoder[RegionSet] = decoder(java.sql.Types.ARRAY, (index, row) ⇒
      row.getArray(index).getArray().asInstanceOf[Array[java.lang.Object]].toSet.asInstanceOf[Set[String]]
    )

    //noinspection TypeAnnotation
    implicit val keySchemaMeta = schemaMeta[DBKey]("sc_keys", _.id → "key_id",
      _.forEncryption → "for_encryption", _.forDecryption → "for_decryption",
      _.key → "serialized_key")
  }

  import schema._

  //noinspection TypeAnnotation
  private[this] object queries {
    val keys = quote {
      query[DBKey]
    }

    def addKey(key: DBKey) = quote {
      keys.insert(lift(key))
    }

    def modifyKey(keyId: KeyId, regionSet: RegionSet, forEncryption: Boolean, forDecryption: Boolean) = quote {
      keys
        .filter(_.id == lift(keyId))
        .update(_.regionSet → lift(regionSet), _.forEncryption → lift(forEncryption), _.forDecryption → lift(forDecryption))
    }
  }

  // -----------------------------------------------------------------------
  // Conversions
  // -----------------------------------------------------------------------
  private[this] object conversions {
    def toDBKey(keySet: KeySet, regionSet: RegionSet, forEncryption: Boolean, forDecryption: Boolean): DBKey = {
      DBKey(keySet.id, regionSet, forEncryption, forDecryption, sc.serialization.toBytes(keySet))
    }

    def toKeySet(key: DBKey): KeySet = {
      sc.serialization.fromBytes[KeySet](key.key)
    }
  }

  // -----------------------------------------------------------------------
  // Key manager functions
  // -----------------------------------------------------------------------
  def addKeySet(keySet: KeySet, regionSet: RegionSet, forEncryption: Boolean, forDecryption: Boolean): Future[KeySet] = {
    Future.fromTry(Try {
      context.run(queries.addKey(conversions.toDBKey(keySet, regionSet, forEncryption, forDecryption)))
      keySet
    })
  }

  def modifyKeySet(keyId: KeyId, regionSet: RegionSet, forEncryption: Boolean, forDecryption: Boolean) = {
    Future.fromTry(Try {
      context.run(queries.modifyKey(keyId, regionSet, forEncryption, forDecryption))
      Done
    })
  }

  def getKeyChain(): Future[KeyChain] = {
    def readKey(bs: ByteString): KeySet = sc.serialization.fromBytes[KeySet](bs)
    Future.fromTry(Try {
      val keys = context.run(queries.keys).map { dk ⇒
        KeyProps(readKey(dk.key), dk.regionSet, dk.forEncryption, dk.forDecryption)
      }

      KeyChain(keys)
    })
  }
}
