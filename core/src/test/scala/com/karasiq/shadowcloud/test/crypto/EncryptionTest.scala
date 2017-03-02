package com.karasiq.shadowcloud.test.crypto

import com.karasiq.shadowcloud.crypto.EncryptionMethod
import com.karasiq.shadowcloud.providers.ModuleRegistry
import com.karasiq.shadowcloud.test.utils.TestUtils._
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.language.postfixOps

class EncryptionTest extends FlatSpec with Matchers {
  val modules = ModuleRegistry(ConfigFactory.load().getConfig("shadowcloud"))
  val plainModule = modules.encryptionModule(EncryptionMethod.Plain)
  val plainParameters = plainModule.createParameters()

  "Plain module" should "process data" in {
    val data = randomBytes(100)
    plainModule.init(encrypt = true, plainParameters)
    val encrypted = plainModule.process(data) ++ plainModule.finish()
    encrypted shouldBe data
    plainModule.init(encrypt = false, plainParameters)
    val decrypted = plainModule.process(encrypted)
    decrypted shouldBe data
  }

  val aesMethod = EncryptionMethod.AES()
  val aesModule = modules.encryptionModule(aesMethod)
  val aesParameters = aesModule.createParameters()

  "AES module" should "generate key" in {
    aesParameters.key.length shouldBe (aesMethod.bits / 8)
    aesParameters.iv should not be empty
    println(s"Key = ${aesParameters.key.toHexString}, iv = ${aesParameters.iv.toHexString}")
  }

  it should "encrypt data" in {
    val data = randomBytes(100)
    aesModule.init(encrypt = true, aesParameters)
    val encrypted = aesModule.process(data) ++ aesModule.finish()
    encrypted should not be data
    encrypted.length should be >= data.length
    aesModule.init(encrypt = false, aesParameters)
    val decrypted = aesModule.process(encrypted) ++ aesModule.finish()
    decrypted shouldBe data
    aesModule.encrypt(decrypted, aesParameters) shouldBe encrypted // Restore
  }
}
