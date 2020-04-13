package com.karasiq.shadowcloud.test.storage

import scala.collection.JavaConverters._
import org.scalatest.{FlatSpec, Matchers}
import com.karasiq.common.encoding.HexString
import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.storage.utils.ChunkKeyMapper
import com.karasiq.shadowcloud.storage.utils.mappers.{CompositeKeyMapper, HashNonceHMACKeyMapper}
import com.karasiq.shadowcloud.test.utils.TestUtils
import com.karasiq.shadowcloud.utils.Utils

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
    val mapper = new HashNonceHMACKeyMapper(Utils.emptyConfig)
    mapper(testChunk.copy(encryption = TestUtils.testSymmetricParameters)) shouldBe
      HexString.decode("e9efc22988788eba5f350dee322cfce28d0d76a900170dab93dbe36a18682359")
  }

  it should "create SHA1 HMAC" in {
    val mapper = new HashNonceHMACKeyMapper(ConfigProps.toConfig(ConfigProps("algorithm" → "HmacSHA1")))
    mapper(testChunk.copy(encryption = TestUtils.testSymmetricParameters)) shouldBe
      HexString.decode("d335659cc935ba592443781cfeda75c8181574d4")
  }

  it should "create MD5 HMAC" in {
    val mapper = new HashNonceHMACKeyMapper(ConfigProps.toConfig(ConfigProps("algorithm" → "HmacMD5")))
    mapper(testChunk.copy(encryption = TestUtils.testSymmetricParameters)) shouldBe
      HexString.decode("ab95bb70ca277d604bbb86331a899be1")
  }

  it should "create multi-iteration HMAC" in {
    val config = ConfigProps.toConfig(ConfigProps(
      "class" → "com.karasiq.shadowcloud.storage.utils.mappers.HashNonceHMACKeyMapper",
      "algorithm" → "HmacSHA1",
      "iterations" → 100
    ))

    val mapper = ChunkKeyMapper.forConfig(config)
    mapper(testChunk.copy(encryption = TestUtils.testSymmetricParameters)) shouldBe
      HexString.decode("2fef032f823de02237979eff53ec9cbd33508b4f")
  }

  "Composite key mapper" should "create composite key" in {
    val mapper = new CompositeKeyMapper(ConfigProps.toConfig(ConfigProps(
      "mappers" → Seq("hmac-mapper", "hash").asJava,
      "hmac-mapper" → Map("class" → "com.karasiq.shadowcloud.storage.utils.mappers.HashNonceHMACKeyMapper", "algorithm" → "HmacSHA1").asJava
    )))
    mapper(testChunk.copy(encryption = TestUtils.testSymmetricParameters)) shouldBe
      HexString.decode("d335659cc935ba592443781cfeda75c8181574d4f660847d03634f41c45f7be337b02973a083721a")
  }

  it should "create composite XOR key" in {
    val mapper = new CompositeKeyMapper(ConfigProps.toConfig(ConfigProps(
      "strategy" → "xor",
      "mappers" → Seq("hmac-mapper", "hash").asJava,
      "hmac-mapper" → Map("class" → "com.karasiq.shadowcloud.storage.utils.mappers.HashNonceHMACKeyMapper", "algorithm" → "HmacSHA1").asJava
    )))
    mapper(testChunk.copy(encryption = TestUtils.testSymmetricParameters)) shouldBe
      HexString.decode("2555e1e1ca56f518e01c03ffc96a5cbbb89606ce")
  }
}
