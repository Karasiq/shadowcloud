package com.karasiq.shadowcloud.test.serialization

import akka.util.ByteString
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.index.{Chunk, File, Folder}
import com.karasiq.shadowcloud.serialization.SerializationModule
import com.karasiq.shadowcloud.test.utils.{TestImplicits, TestUtils}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source
import scala.language.postfixOps

class SerializeTest extends FlatSpec with Matchers with TestImplicits {
  val kryo = SerializationModule.kryo

  "Kryo serializer" should "serialize config" in {
    val config = ConfigFactory.load()
    val bytes = kryo.toBytes(config)
    kryo.fromBytes[Config](bytes) shouldBe config 
  }

  it should "serialize chunk" in {
    val chunk = TestUtils.randomChunk
    val bytes = kryo.toBytes(chunk)
    kryo.fromBytes[Chunk](bytes) shouldBe chunk
  }

  it should "serialize file" in {
    val file = TestUtils.randomFile()
    val bytes = kryo.toBytes(file)
    kryo.fromBytes[File](bytes) shouldBe file
  }

  it should "serialize folder" in {
    val folder = TestUtils.randomFolder()
    val bytes = kryo.toBytes(folder)
    kryo.fromBytes[Folder](bytes) shouldBe folder
  }

  it should "serialize diff" in {
    val diff = TestUtils.testDiff
    val bytes = kryo.toBytes(diff)
    kryo.fromBytes[IndexDiff](bytes) shouldBe diff
    println(bytes.toHexString)
  }

  it should "read test diff" in {
    val diff = TestUtils.testDiff
    val bytes = Source.fromResource("test-diff-kryo.txt").mkString.trim()
    kryo.fromBytes[IndexDiff](ByteString.fromHexString(bytes)) shouldBe diff
  }
}
