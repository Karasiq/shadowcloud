package com.karasiq.shadowcloud.test.storage

import java.nio.file.Files

import akka.stream.scaladsl.{Keep, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.{BaseRepository, Repositories, RepositoryKeys, StorageIOResult}
import com.karasiq.shadowcloud.streams.ByteStringConcat
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}
import org.scalatest.FlatSpecLike

import scala.language.postfixOps

class RepositoryTest extends ActorSpec with FlatSpecLike {
  "In-memory repository" should "store chunk" in {
    testRepository(Repositories.inMemory)
  }

  "File repository" should "store chunk" in {
    testRepository(Repositories.fromDirectory(Files.createTempDirectory("crp-test")).subRepository("default"))
  }

  it should "validate path" in {
    intercept[IllegalArgumentException](Repositories.fromDirectory(Files.createTempFile("crp-test", "file")))
  }

  private[this] def testRepository(repository: BaseRepository): Unit = {
    val chunk = TestUtils.randomChunk
    val testRepository = RepositoryKeys.toHexString(repository)

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
      result.isFailure shouldBe true
    }

    // Delete
    val deleteResult = testRepository.delete(chunk.checksum.hash)
    whenReady(deleteResult) { result ⇒
      val StorageIOResult.Success(_, count) = result
      count shouldBe chunk.data.plain.length
      val keys = testRepository.keys.runWith(TestSink.probe)
      keys.request(1)
      keys.expectComplete()
    }
  }
}
