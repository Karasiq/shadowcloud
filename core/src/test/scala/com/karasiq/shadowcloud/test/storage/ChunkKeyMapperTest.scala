package com.karasiq.shadowcloud.test.storage

import org.scalatest.{FlatSpec, Matchers}

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.storage.utils.ChunkKeyMapper
import com.karasiq.shadowcloud.storage.utils.mappers.HashNonceHMAC
import com.karasiq.shadowcloud.test.utils.TestUtils
import com.karasiq.shadowcloud.utils.{HexString, Utils}

class ChunkKeyMapperTest extends FlatSpec with Matchers {
  val testChunk = TestUtils.testChunk

  "Default chunk key mappers" should "extract keys" in {
    ChunkKeyMapper.hash(testChunk) shouldBe
      HexString.decode("f660847d03634f41c45f7be337b02973a083721a")

    ChunkKeyMapper.encryptedHash(testChunk) shouldBe
      HexString.decode("f660847d03634f41c45f7be337b02973a083721a")

    ChunkKeyMapper.doubleHash(testChunk) shouldBe
      HexString.decode("f660847d03634f41c45f7be337b02973a083721af660847d03634f41c45f7be337b02973a083721a")
  }

  "Hash/nonce HMAC key mapper" should "create SHA256 HMAC" in {
    val mapper = new HashNonceHMAC(Utils.emptyConfig)
    mapper(testChunk.copy(encryption = TestUtils.testSymmetricParameters)) shouldBe
      HexString.decode("e9efc22988788eba5f350dee322cfce28d0d76a900170dab93dbe36a18682359")
  }

  it should "create SHA1 HMAC" in {
    val mapper = new HashNonceHMAC(ConfigProps.toConfig(ConfigProps("hash-nonce-hmac-mapper.algorithm" → "HmacSHA1")))
    mapper(testChunk.copy(encryption = TestUtils.testSymmetricParameters)) shouldBe
      HexString.decode("d335659cc935ba592443781cfeda75c8181574d4")
  }

  it should "create MD5 HMAC" in {
    val mapper = new HashNonceHMAC(ConfigProps.toConfig(ConfigProps("hash-nonce-hmac-mapper.algorithm" → "HmacMD5")))
    mapper(testChunk.copy(encryption = TestUtils.testSymmetricParameters)) shouldBe
      HexString.decode("ab95bb70ca277d604bbb86331a899be1")
  }
}
