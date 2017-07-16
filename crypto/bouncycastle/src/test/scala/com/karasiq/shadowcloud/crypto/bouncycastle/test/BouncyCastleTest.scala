package com.karasiq.shadowcloud.crypto.bouncycastle.test

import java.security.NoSuchAlgorithmException

import scala.language.postfixOps

import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.crypto.bouncycastle.BouncyCastleCryptoProvider
import com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric.{ECIESCipherModule, RSACipherModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.{BCDigests, MessageDigestModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.sign.{ECDSASignModule, RSASignModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.symmetric.{AEADBlockCipherModule, StreamCipherModule}
import com.karasiq.shadowcloud.utils.HexString

class BouncyCastleTest extends FlatSpec with Matchers {
  val provider = new BouncyCastleCryptoProvider
  val testData = ByteString("# First, make a nonce: A single-use value never repeated under the same key\n# The nonce isn't secret, and can be sent with the ciphertext.\n# The cipher instance has a nonce_bytes method for determining how many bytes should be in a nonce")

  // -----------------------------------------------------------------------
  // Encryption
  // -----------------------------------------------------------------------
  testSymmetricEncryption("AES/GCM", AEADBlockCipherModule.AES_GCM(), 32, 12)
  testSymmetricEncryption("Salsa20", StreamCipherModule.Salsa20(), 32, 8)
  testSymmetricEncryption("XSalsa20", StreamCipherModule.XSalsa20(), 32, 24)
  testSymmetricEncryption("ChaCha20", StreamCipherModule.ChaCha20(), 32, 8)

  testAsymmetricEncryption("RSA", RSACipherModule(EncryptionMethod("RSA", 1024)))
  testAsymmetricEncryption("ECIES", ECIESCipherModule(EncryptionMethod("ECIES", 521)))

  // -----------------------------------------------------------------------
  // Hashes
  // -----------------------------------------------------------------------
  val testHashes = Seq(
    "1b1ecddca9aeacf61e56b059ebba904d7556f2f7708298ad50666b297d982726",
    "5ce1b271c63e92ded87414a5f54a990f56f77d67796b98fc512e8f77542547b9",
    "4e895c898f8e528b015765b5fe249f6a",
    "990b4eccd8294682ea0d1f49c6e61c3d",
    "b6edb34b60a98bcd9272359b27065ea2",
    "9092d8cdf95fdf6e863204b844b48f88acf74414",
    "4fbca748243adc8c6e9f303d54be9adc",
    "a28e33efe405a4d8680e09688bd4227964b03da4",
    "67d64b31980590f553806a43a4a7946b2d9f0aa99430afeb6992d2f03df29caf",
    "cd5a67223ed15d87c1f5bfba5226256a5578126bff0106c2e8e6743204df64306324277d5e1519a8",
    "d6d7ec52fbbb5ca8fc5a1cf32e91cf5fcdb3940bf3ce9fc02e4c93ed",
    "e3fc39605cd8e9245ed8cb41e2730c940e6026b9d2f72ead3b0f2d271e2290e0", // SHA-256
    "34f9d73f9797df99ea73247512ddfe93a518791a3fa1d00cdf5760a801b63a013e7fa9086b64907740d8bf9930a1d9c4",
    "11bba64289c2fefc6caf753cc14fd3b914663f0035b0e2135bb29fc5159f9e99ddc57c577146688f4b64cfae09d9be933c22b17eb4a08cdb92e2c1d68efa0f59", // SHA-512
    "25a9624ad4a99cc0af46f37211d76aea591ba166ba66d68a4307195389f6fcf2",
    "a07288c021ff5342cdaf3947a327124ca91bf9008e1de6ef91befed0a91a202b",
    "510da5764778db12d7c094b86ac0f7aaf0a18eca4cedc0048dfd25f2039f3eea",
    "655cbea331b81708a8e1392ae0a5ba54c558f440b3a254ee",
    "44444854782341ac2e2e8076a8a97880d5d2f83e90f087544af94315d71c585d65f68a58174ede4e34ccb4daae71ff60ab3232a6ec521704b8560cb0b6472688",
    "824396f4585a22b2c4b36df76f55e669d4edfb423970071b6b616ce454a95400" // Blake2b
  )

  "Test hashes" should "exist" in {
    BCDigests.algorithms.length shouldBe testHashes.length
  }

  BCDigests.algorithms.zip(testHashes).foreach { case (alg, hash) ⇒
    testHashing(alg, hash)
  }

  testHashing("Blake2-512", MessageDigestModule(HashingMethod("Blake2b", config = ConfigProps("digest-size" → 512))),
    "9f84251be0c325ad771696302e9ed3cd174f84ffdd0b8de49664e9a3ea934b89a4d008581cd5803b80b3284116174b3c4a79a5029996eb59edc1fbacfd18204e")
  testHashing("Skein-1024-1024", MessageDigestModule(HashingMethod("Skein", config = ConfigProps("state-size" → 1024, "digest-size" → 1024))),
    "66588dbe2beb3b9cea762f42e3abaa9dc406bfa005fed3579089d8d2c5807453aa6cb0f8e69134ad47405c843e9a08c51da931827957f06ca58b3e8fe658993e" +
      "1ca87d19a09bc168cc5845bc3235050f8dd59c8f8ec302bbdff4508b16c1c7cef694e1a4c84c250132d445637e0a84772196162a5815c38e45ff3dac4374f567")

  // -----------------------------------------------------------------------
  // Signatures
  // -----------------------------------------------------------------------
  val signHashing = HashingMethod("SHA3")
  testSignature("RSA", RSASignModule(SignMethod("RSA", signHashing, 1024)))
  testSignature("ECDSA", ECDSASignModule(SignMethod("ECDSA", signHashing, 521)))
  testSignature("ECDSA (25519)", ECDSASignModule(SignMethod("ECDSA", signHashing, 128, config = ConfigProps("curve" → "curve25519"))))

  // -----------------------------------------------------------------------
  // Tests specification
  // -----------------------------------------------------------------------
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
    }
  }

  private[this] def testHashing(name: String, testHash: String): Unit = {
    try {
      val module = provider.hashing(HashingMethod(name))
      testHashing(module.method.algorithm, module, testHash)
    } catch {
      case _: NoSuchAlgorithmException ⇒
        println(s"No such algorithm: $name")
    }
  }

  private[this] def testAsymmetricEncryption(name: String, module: EncryptionModule): Unit = {
    s"$name module" should "generate key" in {
      val parameters = EncryptionParameters.asymmetric(module.createParameters())
      val parameters1 = EncryptionParameters.asymmetric(module.updateParameters(parameters))
      parameters1 shouldBe parameters
      println(parameters)
    }

    it should "encrypt data" in {
      val parameters = module.createParameters()
      val encrypted = module.encrypt(testData, parameters)
      encrypted.length should be >= testData.length
      val decrypted = module.decrypt(encrypted, parameters)
      decrypted shouldBe testData
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
    }
  }

  private[this] def testHashing(name: String, module: HashingModule, testHash: String): Unit = {
    s"$name module" should "generate hash" in {
      val hash = HexString.encode(module.createHash(testData))
      // println('"' + hash + '"' + ",")
      hash shouldBe testHash
    }
  }
}
