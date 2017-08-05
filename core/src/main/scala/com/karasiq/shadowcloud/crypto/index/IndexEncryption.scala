package com.karasiq.shadowcloud.crypto.index

import java.util.UUID

import scala.language.postfixOps
import scala.util.hashing.MurmurHash3

import akka.util.ByteString

import com.karasiq.shadowcloud.config.keys.{KeyChain, KeySet}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionParameters, SignMethod}
import com.karasiq.shadowcloud.exceptions.CryptoException
import com.karasiq.shadowcloud.providers.SCModules
import com.karasiq.shadowcloud.serialization.SerializationModule
import com.karasiq.shadowcloud.serialization.protobuf.index.EncryptedIndexData
import com.karasiq.shadowcloud.utils.UUIDUtils

private[shadowcloud] object IndexEncryption {
  def getKeyHash(dataId: UUID, keyId: UUID): Int = {
    MurmurHash3.arrayHash((UUIDUtils.uuidToBytes(keyId) ++ UUIDUtils.uuidToBytes(dataId)).toArray)
  }

  def apply(modules: SCModules, keyEncMethod: EncryptionMethod,
            signMethod: SignMethod, serialization: SerializationModule): IndexEncryption = {
    new IndexEncryption(modules, keyEncMethod, signMethod, serialization)
  }
}

private[shadowcloud] final class IndexEncryption(modules: SCModules, keyEncMethod: EncryptionMethod,
                                                 signMethod: SignMethod, serialization: SerializationModule) {
  def encrypt(payload: ByteString, method: EncryptionMethod, keys: KeyChain): EncryptedIndexData = {
    val keyEncModule = modules.crypto.encryptionModule(keyEncMethod)
    val signModule = modules.crypto.signModule(signMethod)

    def createEncryptedData(data: ByteString, method: EncryptionMethod): (EncryptedIndexData, EncryptionParameters) = {
      val dataId = UUID.randomUUID()
      val encryption = modules.crypto.encryptionModule(method)
      val parameters = encryption.createParameters()
      val encrypted = encryption.encrypt(data, parameters)
      (EncryptedIndexData(dataId, data = encrypted), parameters)
    }

    def createHeader(data: EncryptedIndexData, parameters: EncryptionParameters, keys: KeySet): EncryptedIndexData.Header = {
      val headerData = keyEncModule.encrypt(serialization.toBytes(parameters), keys.encryption)
      val header = EncryptedIndexData.Header(IndexEncryption.getKeyHash(data.id, keys.id), headerData)
      IndexSignatures.sign(data, header, signModule, keys.signing)
    }

    val (encData, encParameters) = createEncryptedData(payload, method)
    val headers = keys.encKeys.map(keySet ⇒ createHeader(encData, encParameters, keySet))
    encData.copy(headers = headers.toVector)
  }

  def decrypt(data: EncryptedIndexData, keys: KeyChain): ByteString = {
    val keyEncModule = modules.crypto.encryptionModule(keyEncMethod)
    val signModule = modules.crypto.signModule(signMethod)

    val matchingKeys = data.headers.iterator.flatMap { header ⇒
      keys.decKeys
        .find(key ⇒ IndexEncryption.getKeyHash(data.id, key.id) == header.keyHash)
        .map((_, header))
        .filter { case (key, header) ⇒ IndexSignatures.verify(data, header, signModule, key.signing) }
    }

    if (matchingKeys.isEmpty) throw CryptoException.KeyMissing()
    val (keySet, header) = matchingKeys.next()

    val parameters = serialization.fromBytes[EncryptionParameters](keyEncModule.decrypt(header.data, keySet.encryption))
    val encryption = modules.crypto.encryptionModule(parameters.method)
    encryption.decrypt(data.data, parameters)
  }
}
