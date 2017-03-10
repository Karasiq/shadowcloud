package com.karasiq.shadowcloud.test.storage

import java.nio.file.Files

import akka.stream.scaladsl.{Keep, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.Repository
import com.karasiq.shadowcloud.storage.Repository.BaseRepository
import com.karasiq.shadowcloud.storage.wrappers.RepositoryWrappers
import com.karasiq.shadowcloud.streams.ByteStringConcat
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}
import org.scalatest.FlatSpecLike

import scala.language.postfixOps

class ChunkRepositoryTest extends ActorSpec with FlatSpecLike {
  def testRepository(repository: BaseRepository): Unit = {
    val chunk = TestUtils.randomChunk
    val testRepository = RepositoryWrappers.hexString(repository)

    // Write chunk
    val (write, writeResult) = TestSource.probe[ByteString]
      .alsoTo(testRepository.write(chunk.checksum.hash))
      .toMat(TestSink.probe)(Keep.both)
      .run()
    write.sendNext(chunk.data.plain)
    write.sendComplete()
    writeResult.requestNext(chunk.data.plain)
    writeResult.expectComplete()

    // Enumerate chunks
    val keys = testRepository.keys.runWith(TestSink.probe)
    keys.requestNext(chunk.checksum.hash)
    keys.request(1)
    keys.expectComplete()

    // Read chunk
    val read = testRepository.read(chunk.checksum.hash).via(ByteStringConcat()).runWith(TestSink.probe)
    read.requestNext(chunk.data.plain)
    read.expectComplete()

    // Rewrite error
    val rewriteBytes = TestUtils.randomBytes(chunk.data.plain.length)
    val rewriteResult = Source.single(rewriteBytes)
      .runWith(testRepository.write(chunk.checksum.hash))

    whenReady(rewriteResult) { result ⇒
      result.count shouldBe 0L
      result.status.isFailure shouldBe true
    }
  }

  "In-memory repository" should "store chunk" in {
    testRepository(Repository.inMemory)
  }

  "File repository" should "store chunk" in {
    testRepository(Repository.fromDirectory(Files.createTempDirectory("crp-test")).subRepository("default"))
  }

  it should "validate path" in {
    intercept[IllegalArgumentException](Repository.fromDirectory(Files.createTempFile("crp-test", "file")))
  }
}
