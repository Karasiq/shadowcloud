package com.karasiq.shadowcloud.crypto.libsodium.test

import akka.util.ByteString
import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto.libsodium.hashing.{Blake2bModule, MultiPartHashModule}
import com.karasiq.shadowcloud.crypto.libsodium.internal._
import com.karasiq.shadowcloud.crypto.libsodium.symmetric._
import com.karasiq.shadowcloud.crypto.{EncryptionModule, HashingMethod, HashingModule}
import com.karasiq.shadowcloud.utils.HexString
import org.scalatest.{FlatSpec, Matchers}

import scala.language.postfixOps

class LibSodiumTest extends FlatSpec with Matchers {
  val testData = ByteString("# First, make a nonce: A single-use value never repeated under the same key\n# The nonce isn't secret, and can be sent with the ciphertext.\n# The cipher instance has a nonce_bytes method for determining how many bytes should be in a nonce")

  if (LSUtils.libraryAvailable) {
    // Encryption
    testEncryption("XSalsa20/Poly1305", SecretBoxModule(), SecretBoxModule.KEY_BYTES, SecretBoxModule.NONCE_BYTES)
    testEncryption("ChaCha20/Poly1305", AEADCipherModule.ChaCha20_Poly1305(), AEADCipherModule.KEY_BYTES, AEADCipherModule.NONCE_BYTES)
    testEncryption("Salsa20", Salsa20Module(), Salsa20Module.KEY_BYTES, Salsa20Module.NONCE_BYTES)
    testEncryption("XSalsa20", XSalsa20Module(), XSalsa20Module.KEY_BYTES, XSalsa20Module.NONCE_BYTES)
    testEncryption("ChaCha20", ChaCha20Module(), ChaCha20Module.KEY_BYTES, ChaCha20Module.NONCE_BYTES)

    if (LSUtils.aes256GcmAvailable) {
      testEncryption("AES/GCM", AEADCipherModule.AES_GCM(), AEADCipherModule.AES_KEY_BYTES, AEADCipherModule.AES_NONCE_BYTES)
    } else {
      println("Hardware AES not supported")
    }

    // Hashes
    testHashing("SHA256", MultiPartHashModule.SHA256(),
      "e3fc39605cd8e9245ed8cb41e2730c940e6026b9d2f72ead3b0f2d271e2290e0")
    testHashing("SHA512", MultiPartHashModule.SHA512(),
      "11bba64289c2fefc6caf753cc14fd3b914663f0035b0e2135bb29fc5159f9e99ddc57c577146688f4b64cfae09d9be933c22b17eb4a08cdb92e2c1d68efa0f59")
    testHashing("Blake2b", Blake2bModule(),
      "824396f4585a22b2c4b36df76f55e669d4edfb423970071b6b616ce454a95400")
    testHashing("Blake2b-512", Blake2bModule(HashingMethod("Blake2b", config = ConfigProps("digest-size" → 512))),
      "9f84251be0c325ad771696302e9ed3cd174f84ffdd0b8de49664e9a3ea934b89a4d008581cd5803b80b3284116174b3c4a79a5029996eb59edc1fbacfd18204e")
  } else {
    println("No libsodium found, tests skipped")
  }

  private[this] def testEncryption(name: String, module: EncryptionModule, keySize: Int, nonceSize: Int): Unit = {
    s"$name module" should "generate key" in {
      val parameters = module.createParameters()
      parameters.symmetric.key.length shouldBe keySize
      parameters.symmetric.nonce.length shouldBe nonceSize
      val parameters1 = module.updateParameters(parameters)
      parameters1.symmetric.nonce should not be parameters.symmetric.nonce
    }

    it should "encrypt data" in {
      val parameters = module.createParameters()
      val encrypted = module.encrypt(testData, parameters)
      encrypted.length should be >= testData.length
      val decrypted = module.decrypt(encrypted, parameters)
      decrypted shouldBe testData
    }
  }

  private[this] def testHashing(name: String, module: HashingModule, testHash: String): Unit = {
    s"$name module" should "generate hash" in {
      val hash = HexString.encode(module.createHash(testData))
      // println(hash)
      hash shouldBe testHash
      val hash1 = HexString.encode(module.createHash(testData))
      hash1 shouldBe hash
    }
  }
}