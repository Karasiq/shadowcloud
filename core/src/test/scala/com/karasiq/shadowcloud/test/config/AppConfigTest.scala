package com.karasiq.shadowcloud.test.config

import scala.language.postfixOps

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.crypto.HashingMethod

class AppConfigTest extends FlatSpec with Matchers {
  "Config" should "be loaded" in {
    val config = AppConfig(ConfigFactory.load().getConfig("shadowcloud"))
    config.crypto.hashing.chunks shouldBe HashingMethod("Blake2b")
    config.crypto.hashing.files shouldBe HashingMethod.none
  }
}
