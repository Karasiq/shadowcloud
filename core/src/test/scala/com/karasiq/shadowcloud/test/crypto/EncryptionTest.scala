package com.karasiq.shadowcloud.test.crypto

import java.security.NoSuchAlgorithmException

import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule}
import com.karasiq.shadowcloud.test.utils.TestUtils.{modules, _}
import org.scalatest.{FlatSpec, Matchers}

import scala.language.postfixOps

class EncryptionTest extends FlatSpec with Matchers {
  runTest("", 0, 0)
  runTest("AES/GCM", 32, 12)
  try {
    runTest("Salsa20", 32, 8)
    runTest("ChaCha20", 32, 8)
    runTest("XSalsa20", 32, 24)
    runTest("ChaCha20/Poly1305", 32, 8)
    runTest("XSalsa20/Poly1305", 32, 24)
  } catch {
    case e: NoSuchAlgorithmException ⇒ println(s"Not available: ${e.getMessage}")
  }

  private[this] def runTest(alg: String, keySize: Int, nonceSize: Int): Unit = {
    val module = modules.encryptionModule(EncryptionMethod(alg, keySize * 8))
    s"${if (alg.nonEmpty) alg else "Plain"} module" should "generate key" in {
      val params = module.createParameters().symmetric
      params.key.length shouldBe keySize
      params.nonce.length shouldBe nonceSize
    }

    it should "encrypt data" in {
      testEncryption(module)
    }
  }

  private[this] def testEncryption(module: EncryptionModule): Unit = {
    val data = randomBytes(100)
    val parameters = module.createParameters()
    val encrypted = module.encrypt(data, parameters)
    // encrypted should not be data
    encrypted.length should be >= data.length
    val decrypted = module.decrypt(encrypted, parameters)
    decrypted shouldBe data
    module.encrypt(decrypted, parameters) shouldBe encrypted // Restore
  }
}
