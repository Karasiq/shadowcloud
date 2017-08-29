package com.karasiq.shadowcloud.crypto.index

import java.security.SecureRandom
import java.util.UUID

import scala.language.postfixOps
import scala.util.hashing.MurmurHash3

import akka.util.ByteString

import com.karasiq.shadowcloud.config.keys.{KeyChain, KeySet}
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.exceptions.CryptoException
import com.karasiq.shadowcloud.providers.CryptoModuleRegistry
import com.karasiq.shadowcloud.serialization.SerializationModule
import com.karasiq.shadowcloud.serialization.protobuf.index.EncryptedIndexData
import com.karasiq.shadowcloud.utils.UUIDUtils

private[shadowcloud] trait IndexEncryption {
  def encrypt(plaintext: ByteString, dataEncMethod: EncryptionMethod, keys: KeyChain): EncryptedIndexData

  @throws[CryptoException]
  def decrypt(data: EncryptedIndexData, keys: KeyChain): ByteString
}

private[shadowcloud] object IndexEncryption {
  def getKeyHash(dataId: UUID, keyId: UUID): Int = {
    MurmurHash3.arrayHash((UUIDUtils.toBytes(keyId) ++ UUIDUtils.toBytes(dataId)).toArray)
  }

  def getNonce(dataNonce: ByteString, keyNonce: ByteString): ByteString = {
    require(dataNonce.length == keyNonce.length, "Nonce length not match")
    val bsb = ByteString.newBuilder
    bsb.sizeHint(keyNonce.length)
    for (i ← keyNonce.indices) {
      bsb += (dataNonce(i) ^ keyNonce(i)).toByte
    }
    bsb.result()
  }

  def apply(cryptoModules: CryptoModuleRegistry, serialization: SerializationModule): IndexEncryption = {
    new DefaultIndexEncryption(cryptoModules, serialization)
  }
}

private[shadowcloud] final class DefaultIndexEncryption(cryptoModules: CryptoModuleRegistry,
                                                        serialization: SerializationModule) extends IndexEncryption {
  private[this] lazy val secureRandom = new SecureRandom()

  def encrypt(plaintext: ByteString, dataEncMethod: EncryptionMethod, keys: KeyChain): EncryptedIndexData = {
    def createEncryptedData(plaintext: ByteString, method: EncryptionMethod): (EncryptedIndexData, EncryptionParameters) = {
      val dataId = UUID.randomUUID()
      val dataEncModule = cryptoModules.encryptionModule(method)
      val dataEncParameters = dataEncModule.createParameters()
      val ciphertext = dataEncModule.encrypt(plaintext, dataEncParameters)
      (EncryptedIndexData(id = dataId, data = ciphertext), dataEncParameters)
    }

    def generateNonce(keyEncParameters: EncryptionParameters): ByteString = {
      val nonceLength = keyEncParameters match {
        case _: AsymmetricEncryptionParameters ⇒ 16
        case sp: SymmetricEncryptionParameters ⇒ sp.nonce.length
      }
      val outArray = new Array[Byte](nonceLength)
      secureRandom.nextBytes(outArray)
      ByteString(outArray)
    }

    def createHeader(encData: EncryptedIndexData, dataEncParameters: EncryptionParameters, staticKeys: KeySet): EncryptedIndexData.Header = {
      val keyEncModule = cryptoModules.encryptionModule(staticKeys.encryption.method)
      val signatures = IndexSignatures(cryptoModules, staticKeys.signing.method)

      val dataNonce = generateNonce(staticKeys.encryption)
      val keyEncParameters = alterWithDataNonce(staticKeys.encryption, dataNonce)

      val headerCiphertext = keyEncModule.encrypt(serialization.toBytes(dataEncParameters), keyEncParameters)
      val header = EncryptedIndexData.Header(
        keyHash = IndexEncryption.getKeyHash(encData.id, staticKeys.id),
        nonce = dataNonce,
        data = headerCiphertext
      )

      signatures.sign(encData, header, staticKeys.signing)
    }

    val (encData, dataEncParameters) = createEncryptedData(plaintext, dataEncMethod)
    val headers = keys.encKeys.map(keySet ⇒ createHeader(encData, dataEncParameters, keySet))
    encData.copy(headers = headers.toVector)
  }

  def decrypt(data: EncryptedIndexData, keys: KeyChain): ByteString = {
    val matchingKeys = data.headers.iterator.flatMap { header ⇒
      keys.decKeys
        .find(key ⇒ IndexEncryption.getKeyHash(data.id, key.id) == header.keyHash)
        .map((_, header))
        .filter { case (key, header) ⇒
          val signatures = IndexSignatures(cryptoModules, key.signing.method)
          signatures.verify(data, header, key.signing)
        }
    }

    if (matchingKeys.isEmpty) throw CryptoException.KeyMissing()
    val (keySet, header) = matchingKeys.next()

    val keyEncParameters = alterWithDataNonce(keySet.encryption, header.nonce)
    val keyEncModule = cryptoModules.encryptionModule(keyEncParameters.method)

    val dataEncParameters = serialization.fromBytes[EncryptionParameters](keyEncModule.decrypt(header.data, keyEncParameters))
    val dataEncModule = cryptoModules.encryptionModule(dataEncParameters.method)

    dataEncModule.decrypt(data.data, dataEncParameters)
  }

  private[this] def alterWithDataNonce(keyEncParameters: EncryptionParameters,
                                       dataNonce: ByteString): EncryptionParameters = keyEncParameters match {
    case ap: AsymmetricEncryptionParameters ⇒
      ap

    case sp: SymmetricEncryptionParameters ⇒
      // Nonce should be unique
      sp.copy(nonce = IndexEncryption.getNonce(dataNonce, sp.nonce))
  }
}
