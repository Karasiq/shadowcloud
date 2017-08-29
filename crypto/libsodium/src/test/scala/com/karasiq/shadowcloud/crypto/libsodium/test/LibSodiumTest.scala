package com.karasiq.shadowcloud.crypto.libsodium.test

import scala.language.postfixOps

import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.crypto.libsodium.asymmetric.SealedBoxModule
import com.karasiq.shadowcloud.crypto.libsodium.hashing.{Blake2bModule, MultiPartHashModule}
import com.karasiq.shadowcloud.crypto.libsodium.internal.LSUtils
import com.karasiq.shadowcloud.crypto.libsodium.signing.CryptoSignModule
import com.karasiq.shadowcloud.crypto.libsodium.symmetric._
import com.karasiq.shadowcloud.model.crypto.{EncryptionParameters, HashingMethod}
import com.karasiq.shadowcloud.test.crypto.utils.CryptoTestVectors
import com.karasiq.shadowcloud.utils.HexString

class LibSodiumTest extends FlatSpec with Matchers {
  val testVectors = CryptoTestVectors("libsodium")
  val testData = ByteString("# First, make a nonce: A single-use value never repeated under the same key\n# The nonce isn't secret, and can be sent with the ciphertext.\n# The cipher instance has a nonce_bytes method for determining how many bytes should be in a nonce")

  if (LSUtils.isLibraryAvailable) {
    // Encryption
    testAsymmetricEncryption(SealedBoxModule.algorithm, SealedBoxModule())
    testSymmetricEncryption("XSalsa20/Poly1305", SecretBoxModule(), SecretBoxModule.KeyBytes, SecretBoxModule.NonceBytes)
    testSymmetricEncryption("ChaCha20/Poly1305", AEADCipherModule.ChaCha20_Poly1305(), AEADCipherModule.KeyBytes, AEADCipherModule.NonceBytes)
    testSymmetricEncryption("Salsa20", Salsa20Module(), Salsa20Module.KeyBytes, Salsa20Module.NonceBytes)
    testSymmetricEncryption("XSalsa20", XSalsa20Module(), XSalsa20Module.KeyBytes, XSalsa20Module.NonceBytes)
    testSymmetricEncryption("ChaCha20", ChaCha20Module(), ChaCha20Module.KeyBytes, ChaCha20Module.NonceBytes)

    if (LSUtils.isAesAvailable) {
      testSymmetricEncryption("AES/GCM", AEADCipherModule.AES_GCM(), AEADCipherModule.AESKeyBytes, AEADCipherModule.AESNonceBytes)
    } else {
      System.err.println("Hardware AES not supported")
    }

    // Hashes
    testHashing("SHA256", MultiPartHashModule.SHA256(),
      "e3fc39605cd8e9245ed8cb41e2730c940e6026b9d2f72ead3b0f2d271e2290e0")

    testHashing("SHA512", MultiPartHashModule.SHA512(),
      "11bba64289c2fefc6caf753cc14fd3b914663f0035b0e2135bb29fc5159f9e99ddc57c577146688f4b64cfae09d9be933c22b17eb4a08cdb92e2c1d68efa0f59")

    testHashing("Blake2b", Blake2bModule(),
      "824396f4585a22b2c4b36df76f55e669d4edfb423970071b6b616ce454a95400")

    testHashing("Blake2b+key", Blake2bModule(HashingMethod("Blake2b", config = ConfigProps("digest-key" → "824396f4585a22b2c4b36df76f55e669d4edfb423970071b6b616ce454a95400"))), "717fc02f9817eb24cc3f9e803fddebf81fc18b415537b1ea9bb08691215d162d")
    
    testHashing("Blake2b-512", Blake2bModule(HashingMethod("Blake2b", config = ConfigProps("digest-size" → 512))),
      "9f84251be0c325ad771696302e9ed3cd174f84ffdd0b8de49664e9a3ea934b89a4d008581cd5803b80b3284116174b3c4a79a5029996eb59edc1fbacfd18204e")

    // Signatures
    testSignature("Ed25519", CryptoSignModule())
  } else {
    println("No libsodium found, tests skipped")
  }

  private[this] def testSymmetricEncryption(name: String, module: EncryptionModule, keySize: Int, nonceSize: Int): Unit = {
    s"$name module" should "generate key" in {
      val parameters = EncryptionParameters.symmetric(module.createParameters())
      parameters.key.length shouldBe keySize
      parameters.nonce.length shouldBe nonceSize
      val parameters1 = EncryptionParameters.symmetric(module.updateParameters(parameters))
      parameters1.nonce should not be parameters.nonce
    }

    it should "encrypt data" in {
      val parameters = module.createParameters()
      val encrypted = module.encrypt(testData, parameters)
      encrypted.length should be >= testData.length
      val decrypted = module.decrypt(encrypted, parameters)
      decrypted shouldBe testData
      testVectors.save(name, parameters, testData, encrypted)
    }

    it should "decrypt test vector" in {
      val (parameters, plain, encrypted) = testVectors.load(name)
      val decrypted = module.decrypt(encrypted, parameters)
      decrypted shouldBe plain
      val encrypted1 = module.encrypt(plain, parameters)
      encrypted1 shouldBe encrypted
    }
  }

  private[this] def testAsymmetricEncryption(name: String, module: EncryptionModule): Unit = {
    s"$name module" should "generate key" in {
      val parameters = EncryptionParameters.asymmetric(module.createParameters())
      val parameters1 = EncryptionParameters.asymmetric(module.updateParameters(parameters))
      parameters1 shouldBe parameters
    }

    it should "encrypt data" in {
      val parameters = module.createParameters()
      val encrypted = module.encrypt(testData, parameters)
      encrypted.length should be >= testData.length
      val decrypted = module.decrypt(encrypted, parameters)
      decrypted shouldBe testData
      testVectors.save(name, parameters, testData, encrypted)
    }

    it should "decrypt test vector" in {
      val (parameters, plain, encrypted) = testVectors.load(name)
      val decrypted = module.decrypt(encrypted, parameters)
      decrypted shouldBe plain
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

  private[this] def testSignature(name: String, module: SignModule): Unit = {
    s"$name sign module" should "generate key" in {
      val parameters = module.createParameters()
      parameters.privateKey should not be empty
      println(parameters)
    }

    it should "sign data" in {
      val parameters = module.createParameters()
      val signature = module.sign(testData, parameters)
      module.verify(testData, signature, parameters) shouldBe true
      module.verify(testData, signature.reverse, parameters) shouldBe false 
    }
  }
}
