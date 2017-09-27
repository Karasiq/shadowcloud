package com.karasiq.shadowcloud.test.config

import scala.concurrent.duration._
import scala.language.postfixOps

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

import com.karasiq.shadowcloud.config.{RegionConfig, SCConfig, StorageConfig}
import com.karasiq.shadowcloud.model.crypto.HashingMethod

class SCConfigTest extends FlatSpec with Matchers {
  val rootConfig = ConfigFactory.load().getConfig("shadowcloud")

  "Application config" should "be loaded" in {
    val config = SCConfig(rootConfig)
    config.crypto.hashing.chunks shouldBe HashingMethod("Blake2b")
    config.crypto.hashing.files shouldBe HashingMethod.none
  }

  "Region-specific config" should "be loaded" in {
    val regionConfig = RegionConfig.forId("testRegion", rootConfig)
    regionConfig.dataReplicationFactor shouldBe 0
    regionConfig.indexReplicationFactor shouldBe 3
  }

  "Storage-specific config" should "be loaded" in {
    val storageConfig = StorageConfig.forId("testStorage", rootConfig)
    storageConfig.index.compactThreshold shouldBe 1234
    storageConfig.index.syncInterval shouldBe 111.seconds
  }
}
