package com.karasiq.shadowcloud.test.crypto

import scala.language.postfixOps

import org.scalatest.{FlatSpec, Matchers}

import com.karasiq.shadowcloud.crypto.{HashingMethod, SignMethod, SignModule}
import com.karasiq.shadowcloud.test.utils.TestUtils
import com.karasiq.shadowcloud.test.utils.CoreTestUtils.modules

class SignModuleTest extends FlatSpec with Matchers {
   val hashingMethod = HashingMethod("SHA-512")

  runTest("RSA", 1024)

  private[this] def runTest(alg: String, keySize: Int): Unit = {
    val module = modules.crypto.signModule(SignMethod(alg, hashingMethod, keySize))
    s"${if (alg.nonEmpty) alg else "No-op"} module" should "generate key" in {
      val params = module.createParameters()
      println(params)
      if (alg.nonEmpty) {
        params.privateKey should not be empty
        params.publicKey should not be empty
      }
    }

    it should "sign data" in {
      testSignature(module)
    }
  }

  private[this] def testSignature(module: SignModule): Unit = {
    val data = TestUtils.randomBytes(100)
    val parameters = module.createParameters()
    val signature = module.sign(data, parameters)
    module.verify(data, signature, parameters) shouldBe true
  }

  private[this] def runCrossTest(method1: SignMethod, method2: SignMethod): Unit = {
    def toString(m: SignMethod) = s"${m.provider.capitalize} (${m.algorithm})"
    val module1 = modules.crypto.signModule(method1)
    val module2 = modules.crypto.signModule(method2)
    s"${toString(method1)}" should s"create compatible signature for ${toString(method2)}" in {
      val data = TestUtils.randomBytes(100)
      val parameters = module1.createParameters()
      val signature = module1.sign(data, parameters)
      val result = module2.verify(data, signature, parameters)
      result shouldBe true
    }
  }

  private[this] def runDoubleCrossTest(method1: SignMethod, method2: SignMethod): Unit = {
    runCrossTest(method1, method2)
    runCrossTest(method2, method1)
  }

  //noinspection NameBooleanParameters
  private[this] def runDoubleCrossTest(provider1: String, provider2: String, alg: String, keySize: Int = 2048): Unit = {
    runDoubleCrossTest(SignMethod(alg, hashingMethod, keySize, false, provider1), SignMethod(alg, hashingMethod, keySize, false, provider2))
  }
}
