package com.karasiq.shadowcloud.test.compression

import java.io.{ObjectInputStream, ObjectOutputStream}
import java.nio.file.{Files, Paths}

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.compression.StreamCompression
import com.karasiq.shadowcloud.compression.StreamCompression.CompressionType
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.karasiq.shadowcloud.test.utils.{ActorSpec, ResourceUtils, TestUtils}

class StreamCompressionTest extends ActorSpec with FlatSpecLike {
  CompressionType.values.foreach(testCompressionStream)

  private[this] def testCompressionStream(compType: CompressionType.Value): Unit = {
    val testBytes = TestUtils.indexedBytes._1

    def testCompression(uncompressed: ByteString): ByteString = {
      val futureCompressed = Source.fromIterator(() ⇒ uncompressed.grouped(100))
        .via(StreamCompression.compress(compType))
        .via(ByteStreams.concat)
        .runWith(Sink.head)

      val compressed = futureCompressed.futureValue
      compressed should not be empty
      compressed
    }

    def testDecompression(compressed: ByteString, expected: ByteString): Unit = {
      val futureUncompressed = Source.fromIterator(() ⇒ compressed.grouped(33))
        .via(StreamCompression.decompress)
        .via(ByteStreams.concat)
        .runWith(Sink.head)

      val uncompressed = futureUncompressed.futureValue
      uncompressed shouldBe expected
    }

    s"$compType" should "compress bytes" in {
      val compressed = testCompression(testBytes)
      // println(s"$compType: ${MemorySize.toString(testBytes.length)} -> ${MemorySize.toString(compressed.length)}")
      // StreamCompressionTest.writeTestVector(compType, testBytes, compressed)
      testDecompression(compressed, testBytes)
    }

    it should "decompress test vector" in {
      val (uncompressed, compressed) = StreamCompressionTest.readTestVector(compType)
      testDecompression(compressed, uncompressed)
      testCompression(uncompressed) shouldBe compressed
    }
  }
}

object StreamCompressionTest {
  private[this] val testVectorsFolder = Paths.get("./utils/.jvm/src/test/resources/compression-vectors")

  def readTestVector(compType: CompressionType.Value): (ByteString, ByteString) = {
    val inputStream = Files.newInputStream(ResourceUtils.getPath(s"compression-vectors/$compType"))
    val objectInputStream = new ObjectInputStream(inputStream)
    val uncompressed = objectInputStream.readObject().asInstanceOf[ByteString]
    val compressed = objectInputStream.readObject().asInstanceOf[ByteString]
    objectInputStream.close()
    (uncompressed, compressed)
  }

  def writeTestVector(compType: CompressionType.Value, uncompressed: ByteString, compressed: ByteString): Unit = {
    if (!Files.isDirectory(testVectorsFolder)) Files.createDirectories(testVectorsFolder)
    val outputStream = Files.newOutputStream(testVectorsFolder.resolve(compType.toString))
    val objectOutputStream = new ObjectOutputStream(outputStream)
    objectOutputStream.writeObject(uncompressed)
    objectOutputStream.writeObject(compressed)
    objectOutputStream.close()
  }
}
