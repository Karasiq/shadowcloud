package com.karasiq.shadowcloud.test.storage

import java.nio.file.Files

import scala.language.postfixOps

import akka.stream.scaladsl.{Keep, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.index.Path
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.repository.{KeyValueRepository, PathTreeRepository, RepositoryKeys}
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.karasiq.shadowcloud.test.utils.{CoreTestUtils, SCExtensionSpec, TestUtils}

class RepositoryTest extends SCExtensionSpec with FlatSpecLike {
  "In-memory repository" should "store chunk" in {
    testRepository(Repositories.inMemory)
  }

  "File repository" should "store chunk" in {
    testRepository(PathTreeRepository.toKeyValue(Repositories.fromDirectory(Files.createTempDirectory("crp-test")), Path.root / "default"))
  }

  it should "validate path" in {
    intercept[IllegalArgumentException](Repositories.fromDirectory(Files.createTempFile("crp-test", "file")))
  }

  private[this] def testRepository(repository: KeyValueRepository): Unit = {
    val chunk = CoreTestUtils.randomChunk
    val testRepository = RepositoryKeys.toHexString(repository)

    // Write chunk
    val (write, writeResult) = TestSource.probe[ByteString]
      .toMat(testRepository.write(chunk.checksum.hash))(Keep.both)
      .run()
    write.sendNext(chunk.data.plain)
    write.sendComplete()
    whenReady(writeResult) { result ⇒
      result.isSuccess shouldBe true
      result.count should not be 0
    }

    // Enumerate chunks
    val keys = testRepository.keys.runWith(TestSink.probe)
    keys.requestNext(chunk.checksum.hash)
    keys.request(1)
    keys.expectComplete()

    // Read chunk
    val read = testRepository.read(chunk.checksum.hash)
      .via(ByteStreams.concat)
      .runWith(TestSink.probe)

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
