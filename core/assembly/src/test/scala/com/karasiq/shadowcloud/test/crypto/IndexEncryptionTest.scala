package com.karasiq.shadowcloud.test.crypto

import java.util.UUID

import org.scalatest.{FlatSpec, Matchers}

import com.karasiq.common.encoding.HexString
import com.karasiq.shadowcloud.crypto.index.IndexEncryption
import com.karasiq.shadowcloud.test.utils.TestUtils

class IndexEncryptionTest extends FlatSpec with Matchers {
  "Index encryption" should "create key hash" in {
    IndexEncryption.getKeyHash(UUID.fromString("fb390d6a-545e-4ea7-a3ac-45e4a9223ebb"),
      UUID.fromString("1ac2ad29-3edb-4f65-9142-cde158f06d28")) shouldBe (-1060735836)
  }
  
  it should "create nonce" in {
    val nonce1 = TestUtils.indexedBytes._1.slice(0, 16)
    val nonce2 = TestUtils.indexedBytes._1.slice(16, 32)
    IndexEncryption.getNonce(nonce1, nonce2) shouldBe HexString.decode("300c10444d171852010e0316000d0010")
  }
}
