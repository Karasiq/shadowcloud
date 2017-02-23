package com.karasiq.shadowcloud.test.crypto

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.HashingModule
import com.karasiq.shadowcloud.test.utils.TestImplicits
import org.scalatest.{FlatSpec, Matchers}

import scala.language.postfixOps

class HashingTest extends FlatSpec with Matchers with TestImplicits {
  val bytes = ByteString("The testing facilities described up to this point were aiming at formulating assertions about a system’s behavior. If a test fails, it is usually your job to find the cause, fix it and verify the test again. This process is supported by debuggers as well as logging, where the Akka toolkit offers the following options:")

  "Hashing module" should "calculate SHA-1" in {
    val hashing = HashingModule("SHA1")
    hashing.createHash(bytes) shouldBe ByteString.fromHexString("b608b0697e31e807fff8a37ae0d0292e62a8bbc2")
  }
}
