package com.karasiq.shadowcloud.crypto.index

import java.security.SecureRandom
import java.util.UUID

import scala.language.postfixOps
import scala.util.hashing.MurmurHash3

import akka.util.ByteString

import com.karasiq.common.encoding.UUIDEncoding
import com.karasiq.shadowcloud.crypto.index.IndexEncryption.{CiphertextT, PlaintextT}
import com.karasiq.shadowcloud.exceptions.CryptoException
import com.karasiq.shadowcloud.model.crypto.{AsymmetricEncryptionParameters, EncryptionMethod, EncryptionParameters, SymmetricEncryptionParameters}
import com.karasiq.shadowcloud.model.keys.{KeyChain, KeyId, KeySet}
import com.karasiq.shadowcloud.providers.CryptoModuleRegistry
import com.karasiq.shadowcloud.serialization.IndexSerialization
import com.karasiq.shadowcloud.serialization.protobuf.index.EncryptedIndexData

private[shadowcloud] trait IndexEncryption {
  def encrypt(plaintext: PlaintextT, dataEncMethod: EncryptionMethod, keys: KeyChain): CiphertextT

  @throws[CryptoException]
  def decrypt(data: CiphertextT, keys: KeyChain): PlaintextT
}

private[shadowcloud] object IndexEncryption {
  private[crypto] type DataId = UUID
  private[crypto] type NonceT = ByteString
  type PlaintextT = ByteString
  type CiphertextT = EncryptedIndexData

  def apply(cryptoModules: CryptoModuleRegistry, serialization: IndexSerialization): IndexEncryption = {
    new DefaultIndexEncryption(cryptoModules, serialization)
  }
  
  def getKeyHash(dataId: DataId, keyId: KeyId): Int = {
    MurmurHash3.arrayHash((UUIDEncoding.toBytes(keyId) ++ UUIDEncoding.toBytes(dataId)).toArray)
  }

  def getNonce(dataNonce: NonceT, keyNonce: NonceT): NonceT = {
    require(dataNonce.length == keyNonce.length, "Nonce length not match")
    val bsb = ByteString.newBuilder
    bsb.sizeHint(keyNonce.length)
    for (i ← keyNonce.indices) {
      bsb += (dataNonce(i) ^ keyNonce(i)).toByte
    }
    bsb.result()
  }

  def updateNonce(keyEncParameters: EncryptionParameters, dataNonce: NonceT): EncryptionParameters = keyEncParameters match {
    case ap: AsymmetricEncryptionParameters ⇒
      ap

    case sp: SymmetricEncryptionParameters ⇒
      // Nonce should be unique
      sp.copy(nonce = getNonce(dataNonce, sp.nonce))
  }
}

private[shadowcloud] final class DefaultIndexEncryption(cryptoModules: CryptoModuleRegistry,
                                                        serialization: IndexSerialization) extends IndexEncryption {
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
      val keyEncParameters = IndexEncryption.updateNonce(staticKeys.encryption, dataNonce)

      val headerCiphertext = keyEncModule.encrypt(serialization.wrapKey(dataEncParameters), keyEncParameters)
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

    val keyEncParameters = IndexEncryption.updateNonce(keySet.encryption, header.nonce)
    val keyEncModule = cryptoModules.encryptionModule(keyEncParameters.method)

    val dataEncParameters = serialization.unwrapKey(keyEncModule.decrypt(header.data, keyEncParameters))
    val dataEncModule = cryptoModules.encryptionModule(dataEncParameters.method)

    dataEncModule.decrypt(data.data, dataEncParameters)
  }
}
