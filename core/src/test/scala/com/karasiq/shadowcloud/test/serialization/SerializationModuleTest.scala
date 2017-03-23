package com.karasiq.shadowcloud.test.serialization

import akka.util.ByteString
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.index.{Chunk, File, Folder}
import com.karasiq.shadowcloud.serialization.{SerializationModule, SerializationModules}
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.FlatSpecLike

import scala.io.Source
import scala.language.postfixOps

class SerializationModuleTest extends ActorSpec with FlatSpecLike {
  testModule("Akka", SerializationModules.fromActorSystem)

  private[this] def testModule(name: String, module: SerializationModule): Unit = {
    name should "serialize config" in {
      val config = ConfigFactory.load()
      val bytes = module.toBytes(config)
      module.fromBytes[Config](bytes) shouldBe config
    }

    it should "serialize chunk" in {
      val chunk = TestUtils.randomChunk
      val bytes = module.toBytes(chunk)
      module.fromBytes[Chunk](bytes) shouldBe chunk
    }

    it should "serialize file" in {
      val file = TestUtils.randomFile()
      val bytes = module.toBytes(file)
      module.fromBytes[File](bytes) shouldBe file
    }

    it should "serialize folder" in {
      val folder = TestUtils.randomFolder()
      val bytes = module.toBytes(folder)
      module.fromBytes[Folder](bytes) shouldBe folder
    }

    it should "serialize diff" in {
      val diff = TestUtils.testDiff
      val bytes = module.toBytes(diff)
      module.fromBytes[IndexDiff](bytes) shouldBe diff
      println(bytes.toHexString)
    }

    ignore should "read test diff" in {
      val diff = TestUtils.testDiff
      val bytes = Source.fromResource(s"test-diff-$name.txt").mkString.trim()
      module.fromBytes[IndexDiff](ByteString.fromHexString(bytes)) shouldBe diff
    }
  }
}
