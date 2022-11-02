package com.karasiq.shadowcloud.test.serialization

import akka.util.ByteString
import com.karasiq.shadowcloud.config.SCConfig
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.model.{Chunk, File, Folder}
import com.karasiq.shadowcloud.serialization.protobuf.index.SerializedIndexData
import com.karasiq.shadowcloud.serialization.{SerializationModule, SerializationModules}
import com.karasiq.shadowcloud.test.utils.{CoreTestUtils, SCExtensionSpec, TestUtils}
import com.typesafe.config.Config
import org.scalatest.FlatSpecLike

import scala.io.Source

class SerializationModuleTest extends SCExtensionSpec with FlatSpecLike {
  testModule("akka", SerializationModules.forActorSystem(system))

  private[this] def testModule(name: String, module: SerializationModule): Unit = {
    s"${name.capitalize} serializer" should "serialize config" in {
      val config = CoreTestUtils.config.rootConfig
      val bytes  = module.toBytes(config)
      module.fromBytes[Config](bytes) shouldBe config
    }

    it should "serialize wrapped config" in {
      val config = CoreTestUtils.config
      val bytes  = module.toBytes(config)
      module.fromBytes[SCConfig](bytes) shouldBe config
    }

    it should "serialize protobuf message" in {
      val message = SerializedIndexData(data = TestUtils.randomBytes(50))
      val bytes   = module.toBytes(message)
      module.fromBytes[SerializedIndexData](bytes) shouldBe message
    }

    it should "serialize chunk" in {
      val chunk = CoreTestUtils.randomChunk
      val bytes = module.toBytes(chunk)
      module.fromBytes[Chunk](bytes) shouldBe chunk
    }

    it should "serialize file" in {
      val file  = CoreTestUtils.randomFile()
      val bytes = module.toBytes(file)
      val file1 = module.fromBytes[File](bytes)
      file1 shouldBe file
      file1.hashCode() shouldBe file.hashCode()
    }

    it should "serialize folder" in {
      val folder = CoreTestUtils.randomFolder()
      val bytes  = module.toBytes(folder)
      module.fromBytes[Folder](bytes) shouldBe folder
    }

    it should "serialize diff" in {
      val diff  = TestUtils.testDiff
      val bytes = module.toBytes(diff)
      module.fromBytes[IndexDiff](bytes) shouldBe diff
      println(bytes.toHexString)
    }

    it should "read test diff" in {
      val diff             = TestUtils.testDiff
      val bytes            = Source.fromResource(s"test-diff-$name.txt").mkString.trim()
      val deserializedDiff = module.fromBytes[IndexDiff](ByteString.fromHexString(bytes))
      deserializedDiff shouldBe diff
    }
  }
}
