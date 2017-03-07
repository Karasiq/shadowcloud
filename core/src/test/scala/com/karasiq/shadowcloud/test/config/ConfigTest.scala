package com.karasiq.shadowcloud.test.config

import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.config.utils.ChunkKeyExtractor
import com.karasiq.shadowcloud.crypto.HashingMethod
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.language.postfixOps

class ConfigTest extends FlatSpec with Matchers {
  "Config" should "be loaded" in {
    val config = AppConfig(ConfigFactory.load().getConfig("shadowcloud"))
    config.index.syncInterval shouldBe (15 seconds)
    config.index.replicationFactor shouldBe 0
    config.crypto.hashing.chunks shouldBe HashingMethod("Blake2b")
    config.crypto.hashing.files shouldBe HashingMethod.none
    config.storage.replicationFactor shouldBe 1
    config.storage.chunkKey shouldBe ChunkKeyExtractor.hash
  }
}
