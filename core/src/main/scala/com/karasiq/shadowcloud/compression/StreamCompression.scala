package com.karasiq.shadowcloud.compression

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source, Compression ⇒ AkkaStdCompression}
import akka.util.ByteString

import com.karasiq.shadowcloud.compression.lz4.LZ4Streams

object StreamCompression {
  object CompressionType extends Enumeration {
    val none = Value(0, "none")
    val gzip = Value(1, "gzip")
    val deflate = Value(2, "deflate")
    val lz4 = Value(3, "lz4")
  }

  def compress(compressionType: CompressionType.Value): Flow[ByteString, ByteString, NotUsed] = {
    assert(compressionType.id < 256)
    Flow[ByteString]
      .via(rawStreams.compress(compressionType))
      .prepend(Source.single(ByteString(compressionType.id.toByte)))
  }

  def decompress: Flow[ByteString, ByteString, NotUsed] = {
    Flow[ByteString].dropWhile(_.isEmpty).prefixAndTail(1).flatMapConcat { case (head +: Nil, stream) ⇒
      val dataStream = Source.single(head.drop(1)).concat(stream)
      val compType = CompressionType(java.lang.Byte.toUnsignedInt(head.head))
      dataStream.via(rawStreams.decompress(compType))
    }
  }

  private[this] object rawStreams {
    def compress(compressionType: CompressionType.Value) = compressionType match {
      case CompressionType.none ⇒
        Flow[ByteString]

      case CompressionType.gzip ⇒
        AkkaStdCompression.gzip

      case CompressionType.deflate ⇒
        AkkaStdCompression.deflate

      case CompressionType.lz4 ⇒
        LZ4Streams.compress
    }

    def decompress(compressionType: CompressionType.Value) = compressionType match {
      case CompressionType.none ⇒
        Flow[ByteString]

      case CompressionType.gzip ⇒
        AkkaStdCompression.gunzip()

      case CompressionType.deflate ⇒
        AkkaStdCompression.inflate()

      case CompressionType.lz4 ⇒
        LZ4Streams.decompress
    }
  }
}
