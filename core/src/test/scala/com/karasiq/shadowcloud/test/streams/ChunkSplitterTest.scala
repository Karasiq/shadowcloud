package com.karasiq.shadowcloud.test.streams

import scala.language.postfixOps

import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.index.{Checksum, Chunk, Data}
import com.karasiq.shadowcloud.streams._
import com.karasiq.shadowcloud.test.utils.{SCExtensionSpec, TestUtils}

//noinspection ZeroIndexToHead
class ChunkSplitterTest extends SCExtensionSpec with FlatSpecLike {
  val (sourceBytes, sourceFile) = TestUtils.indexedBytes
  val hashingMethod = sourceFile.checksum.method
  val sourceHashes = sourceFile.chunks.map(_.checksum.hash)
  val chunkProcessing = sc.streams.chunk

  "Chunk splitter" should "split text" in {
    val fullOut = Source.single(sourceBytes)
      .via(ChunkSplitter(100))
      .via(chunkProcessing.createHashes(hashingMethod))
      .map(_.checksum.hash)
      .runWith(Sink.seq)

    fullOut.futureValue shouldBe sourceFile.chunks.map(_.checksum.hash)
  }

  it should "join text" in {
    val (in, out) = TestSource.probe[ByteString]
      .via(ChunkSplitter(100))
      .via(chunkProcessing.createHashes(hashingMethod, hashingMethod))
      .map(_.checksum.hash)
      .toMat(TestSink.probe)(Keep.both)
      .run()

    in.sendNext(sourceBytes.take(10)).sendNext(sourceBytes.slice(10, 110))
    out.requestNext(sourceHashes(0))
    in.sendNext(sourceBytes.slice(110, 300))
    out.request(2).expectNext(sourceHashes(1), sourceHashes(2))
    in.sendNext(sourceBytes.drop(300))
    in.sendComplete()
    out.requestNext(sourceHashes(3))
    out.request(1)
    out.expectComplete()
  }

  "Chunk encryptor" should "encrypt chunk stream" in {
    def testChunk(chunk: Chunk) = {
      val hasher = chunkProcessing.modules.crypto.hashingModule(chunk.checksum.method)
      val decryptor = chunkProcessing.modules.crypto.encryptionModule(chunk.encryption.method)
      val hash1 = hasher.createHash(chunk.data.plain)
      val hash2 = hasher.createHash(chunk.data.encrypted)
      val size1 = chunk.data.plain.length
      val size2 = chunk.data.encrypted.length
      val data = decryptor.decrypt(chunk.data.encrypted, chunk.encryption)
      chunk shouldBe Chunk(Checksum(chunk.checksum.method, chunk.checksum.method, size1, hash1, size2, hash2), chunk.encryption, Data(data, chunk.data.encrypted))
      chunk
    }

    val result = Source.single(sourceBytes)
      .via(ChunkSplitter(100))
      .via(chunkProcessing.beforeWrite(hashing = hashingMethod, encHashing = hashingMethod))
      .map(testChunk)
      .runWith(sc.streams.chunk.index(hashingMethod, hashingMethod))

    whenReady(result) { file ⇒
      file.chunks.map(_.checksum.hash) shouldBe sourceHashes
      file.checksum.hash shouldBe sourceFile.checksum.hash
      file.chunks.map(_.checksum.hash) shouldBe sourceHashes
    }
  }
}
