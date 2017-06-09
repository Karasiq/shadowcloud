package com.karasiq.shadowcloud.persistence.h2

import java.nio.ByteBuffer
import java.util.UUID

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.config.keys.{KeyChain, KeySet}
import com.karasiq.shadowcloud.persistence.KeyManager
import com.karasiq.shadowcloud.persistence.model.DBKey

class H2KeyManager(h2: H2DBExtension) extends KeyManager {
  import h2.sc
  import h2.context.db
  import db._

  private[this] object encoders {
    private[this] def uuidToBytes(uuid: UUID): Array[Byte] = {
      val bb = ByteBuffer.allocate(16)
      bb.putLong(uuid.getMostSignificantBits)
      bb.putLong(uuid.getLeastSignificantBits)
      bb.flip()
      bb.array()
    }

    private[this] def bytesToUuid(bytes: Array[Byte]): UUID = {
      val bb = ByteBuffer.wrap(bytes, 0, 16)
      new UUID(bb.getLong, bb.getLong)
    }

    //implicit val encodeUUID = MappedEncoding[UUID, Array[Byte]](uuidToBytes)
    //implicit val decodeUUID = MappedEncoding[Array[Byte], UUID](bytesToUuid)

    implicit val encodeByteString = MappedEncoding[ByteString, Array[Byte]](_.toArray)
    implicit val decodeByteString = MappedEncoding[Array[Byte], ByteString](ByteString.fromArray)
  }

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
