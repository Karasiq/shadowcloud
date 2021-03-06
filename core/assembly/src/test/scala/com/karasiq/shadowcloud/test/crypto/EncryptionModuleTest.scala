package com.karasiq.shadowcloud.test.crypto

import java.security.NoSuchAlgorithmException

import com.karasiq.shadowcloud.crypto.EncryptionModule
import com.karasiq.shadowcloud.model.crypto.{EncryptionMethod, EncryptionParameters}
import com.karasiq.shadowcloud.test.utils.CoreTestUtils.modules
import com.karasiq.shadowcloud.test.utils.TestUtils
import org.scalatest.{FlatSpec, Matchers}

class EncryptionModuleTest extends FlatSpec with Matchers {
  runTest("", 0, 0)
  runTest("AES/GCM", 32, 12)
  runTest("Salsa20", 32, 8)
  runTest("ChaCha20", 32, 8)
  runTest("XSalsa20", 32, 24)

  try {
    runTest("ChaCha20/Poly1305", 32, 8)
    runTest("XSalsa20/Poly1305", 32, 24)

    runDoubleCrossTest("bouncycastle", "libsodium", "Salsa20")
    runDoubleCrossTest("bouncycastle", "libsodium", "XSalsa20")
    runDoubleCrossTest("bouncycastle", "libsodium", "ChaCha20")
    runDoubleCrossTest("bouncycastle", "libsodium", "AES/GCM")
  } catch {
    case e: NoSuchAlgorithmException ⇒ println(s"Not available: ${e.getMessage}")
  }

  private[this] def runTest(alg: String, keySize: Int, nonceSize: Int): Unit = {
    val module = modules.crypto.encryptionModule(EncryptionMethod(alg, keySize * 8))
    s"${if (alg.nonEmpty) alg else "Plain"} module" should "generate key" in {
      val params = EncryptionParameters.symmetric(module.createParameters())
      params.key should have length keySize
      params.nonce should have length nonceSize
    }

    it should "encrypt data" in {
      testEncryption(module)
    }
  }

  private[this] def testEncryption(module: EncryptionModule): Unit = {
    val data = TestUtils.randomBytes(100)
    val parameters = module.createParameters()
    val encrypted = module.encrypt(data, parameters)
    // encrypted should not be data
    encrypted.length should be >= data.length
    val decrypted = module.decrypt(encrypted, parameters)
    decrypted shouldBe data
    module.encrypt(decrypted, parameters) shouldBe encrypted // Restore
  }

  private[this] def runCrossTest(method1: EncryptionMethod, method2: EncryptionMethod): Unit = {
    def toString(m: EncryptionMethod) = s"${m.provider.capitalize} (${m.algorithm})"
    val module1 = modules.crypto.encryptionModule(method1)
    val module2 = modules.crypto.encryptionModule(method2)
    s"${toString(method1)}" should s"create compatible data for ${toString(method2)}" in {
      val data = TestUtils.randomBytes(100)
      val parameters = module1.createParameters()
      val encrypted = module1.encrypt(data, parameters)
      val decrypted = module2.decrypt(encrypted, parameters)
      decrypted shouldBe data
    }
  }

  private[this] def runDoubleCrossTest(method1: EncryptionMethod, method2: EncryptionMethod): Unit = {
    runCrossTest(method1, method2)
    runCrossTest(method2, method1)
  }

  //noinspection NameBooleanParameters
  private[this] def runDoubleCrossTest(provider1: String, provider2: String, alg: String, keySize: Int = 256): Unit = {
    runDoubleCrossTest(EncryptionMethod(alg, keySize, provider = provider1), EncryptionMethod(alg, keySize, provider = provider2))
  }
}
