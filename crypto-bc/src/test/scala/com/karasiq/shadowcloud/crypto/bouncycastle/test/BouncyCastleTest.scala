package com.karasiq.shadowcloud.crypto.bouncycastle.test

import java.security.{MessageDigest, NoSuchAlgorithmException}

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{AEADBlockCipherEncryptionModule, BCUtils, MessageDigestHashingModule}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule, HashingMethod}
import com.karasiq.shadowcloud.utils.HexString
import org.scalatest.{FlatSpec, Matchers}

import scala.language.postfixOps

class BouncyCastleTest extends FlatSpec with Matchers {
  val testData = ByteString("# First, make a nonce: A single-use value never repeated under the same key\n# The nonce isn't secret, and can be sent with the ciphertext.\n# The cipher instance has a nonce_bytes method for determining how many bytes should be in a nonce")
  val testHashes = Seq(
    "1b1ecddca9aeacf61e56b059ebba904d7556f2f7708298ad50666b297d982726",
    "4e895c898f8e528b015765b5fe249f6a",
    "990b4eccd8294682ea0d1f49c6e61c3d",
    "b6edb34b60a98bcd9272359b27065ea2",
    "9092d8cdf95fdf6e863204b844b48f88acf74414",
    "4fbca748243adc8c6e9f303d54be9adc",
    "a28e33efe405a4d8680e09688bd4227964b03da4",
    "67d64b31980590f553806a43a4a7946b2d9f0aa99430afeb6992d2f03df29caf",
    "cd5a67223ed15d87c1f5bfba5226256a5578126bff0106c2e8e6743204df64306324277d5e1519a8",
    "d6d7ec52fbbb5ca8fc5a1cf32e91cf5fcdb3940bf3ce9fc02e4c93ed",
    "e3fc39605cd8e9245ed8cb41e2730c940e6026b9d2f72ead3b0f2d271e2290e0",
    "34f9d73f9797df99ea73247512ddfe93a518791a3fa1d00cdf5760a801b63a013e7fa9086b64907740d8bf9930a1d9c4",
    "11bba64289c2fefc6caf753cc14fd3b914663f0035b0e2135bb29fc5159f9e99ddc57c577146688f4b64cfae09d9be933c22b17eb4a08cdb92e2c1d68efa0f59",
    "510da5764778db12d7c094b86ac0f7aaf0a18eca4cedc0048dfd25f2039f3eea",
    "655cbea331b81708a8e1392ae0a5ba54c558f440b3a254ee",
    "44444854782341ac2e2e8076a8a97880d5d2f83e90f087544af94315d71c585d65f68a58174ede4e34ccb4daae71ff60ab3232a6ec521704b8560cb0b6472688"
  )

  testEncryption("AES/GCM", AEADBlockCipherEncryptionModule.AES_GCM(EncryptionMethod("AES/GCM", 256)), 32, 12)

  BCUtils.DIGESTS.zip(testHashes).foreach { case (alg, hash) ⇒
    testHashing(alg, hash)
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

  private[this] def testHashing(name: String, testHash: String): Unit = {
    try {
      val module = new MessageDigestHashingModule(HashingMethod(name), MessageDigest.getInstance(name, BCUtils.provider))
      s"$name module" should "generate hash" in {
        val hash = HexString.encode(module.createHash(testData))
        // println('"' + hash + '"' + ",")
        hash shouldBe testHash
      }
    } catch {
      case _: NoSuchAlgorithmException ⇒
        println(s"No such algorithm: $name")
    }
  }
}
