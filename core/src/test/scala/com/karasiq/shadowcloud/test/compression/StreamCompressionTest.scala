package com.karasiq.shadowcloud.test.compression

import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.compression.StreamCompression
import com.karasiq.shadowcloud.compression.StreamCompression.CompressionType
import com.karasiq.shadowcloud.streams.utils.ByteStringConcat
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}

class StreamCompressionTest extends ActorSpec with FlatSpecLike {
  CompressionType.values.foreach(testCompressionStream)

  private[this] def testCompressionStream(compType: CompressionType.Value): Unit = {
    val testBytes = TestUtils.indexedBytes._1

    s"$compType" should "compress bytes" in {
      val futureCompressed = Source.fromIterator(() ⇒ testBytes.grouped(100))
        .via(StreamCompression.compress(compType))
        .via(ByteStringConcat())
        .runWith(Sink.head)

      val compressed = futureCompressed.futureValue
      compressed should not be empty

      val futureUncompressed = Source.fromIterator(() ⇒ compressed.grouped(33))
        .via(StreamCompression.decompress)
        .via(ByteStringConcat())
        .runWith(Sink.head)

      val uncompressed = futureUncompressed.futureValue
      uncompressed shouldBe testBytes
    }
  }
}
