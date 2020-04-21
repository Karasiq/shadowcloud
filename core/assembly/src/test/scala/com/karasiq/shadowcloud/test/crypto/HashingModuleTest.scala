package com.karasiq.shadowcloud.test.crypto



import akka.util.ByteString
import com.karasiq.shadowcloud.model.crypto.HashingMethod
import com.karasiq.shadowcloud.test.utils.{ByteStringImplicits, CoreTestUtils}
import org.scalatest.{FlatSpec, Matchers}

class HashingModuleTest extends FlatSpec with Matchers with ByteStringImplicits {
  val testData = ByteString("The testing facilities described up to this point were aiming at formulating assertions about a system’s behavior. If a test fails, it is usually your job to find the cause, fix it and verify the test again. This process is supported by debuggers as well as logging, where the Akka toolkit offers the following options:")

  val hashes = Map(
    "SHA1" → "b608b0697e31e807fff8a37ae0d0292e62a8bbc2",
    "SHA256" → "299a5a1fef2c67b6af326841684e020405978a34f7924025c1f5a188d808f08f",
    "SHA512" → "46d69344d4fab2e20441d49a31ba94fbdba3d551fbbeb4646a00d9fd0e4d1a4b27c5c7ff9fcc992f0d88675e4de0dce1a8c8420298a60789d7a76ddf841d8088",
    "Blake2b" → "f38fec1634ceaae44ca6a6ee6f493938b746a9d82581235ca6a748dc21ad865d"
  )

  for ((alg, hash) ← hashes) testModule(alg, hash)

  private[this] def testModule(alg: String, testHash: String): Unit = {
    s"$alg module" should "calculate hash" in {
      val module = CoreTestUtils.modules.crypto.hashingModule(HashingMethod(alg))
      module.createHash(testData).toHexString shouldBe testHash
    }
  }
}
