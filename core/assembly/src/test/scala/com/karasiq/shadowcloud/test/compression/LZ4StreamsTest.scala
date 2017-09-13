package com.karasiq.shadowcloud.test.compression

import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.compression.lz4.LZ4Streams
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.karasiq.shadowcloud.test.utils.{ActorSpec, ActorSpecImplicits, TestUtils}

class LZ4StreamsTest extends ActorSpec with ActorSpecImplicits with FlatSpecLike {
  val testBytes = TestUtils.indexedBytes._1

  "LZ4" should "compress bytes" in {
    val futureCompressed = Source.fromIterator(() ⇒ testBytes.grouped(100))
      .via(LZ4Streams.compress)
      .via(ByteStreams.concat)
      .runWith(Sink.head)

    val compressed = futureCompressed.futureValue
    compressed should not be empty

    val futureUncompressed = Source.fromIterator(() ⇒ compressed.grouped(33))
      .via(LZ4Streams.decompress)
      .via(ByteStreams.concat)
      .runWith(Sink.head)

    val uncompressed = futureUncompressed.futureValue
    uncompressed shouldBe testBytes
  }
}
