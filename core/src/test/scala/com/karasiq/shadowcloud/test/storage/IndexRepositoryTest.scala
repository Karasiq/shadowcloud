package com.karasiq.shadowcloud.test.storage

import java.nio.file.Files

import akka.Done
import akka.stream.IOResult
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.IndexRepository
import com.karasiq.shadowcloud.storage.IndexRepository.BaseIndexRepository
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexRepositoryStreams}
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}
import org.scalatest.FlatSpecLike

import scala.language.postfixOps
import scala.util.Success

class IndexRepositoryTest extends ActorSpec with FlatSpecLike {
  def testRepository(repository: BaseIndexRepository): Unit = {
    val diff = TestUtils.randomDiff
    val testRepository = IndexRepository.numeric(repository)

    // Write diff
    val streams = IndexRepositoryStreams.gzipped
    val (write, writeResult) = TestSource.probe[(Long, IndexDiff)]
      .via(streams.write(testRepository))
      .toMat(TestSink.probe)(Keep.both)
      .run()
    write.sendNext((diff.time, diff))
    val IndexIOResult(diff.time, `diff`, IOResult(_, Success(Done))) = writeResult.requestNext()
    write.sendComplete()
    writeResult.expectComplete()

    // Enumerate diffs
    val keys = testRepository.keys.runWith(TestSink.probe)
    keys.requestNext(diff.time)
    keys.expectComplete()

    // Read diff
    val (read, readResult) = TestSource.probe[Long]
      .via(streams.read(testRepository))
      .toMat(TestSink.probe)(Keep.both)
      .run()
    read.sendNext(diff.time)
    val IndexIOResult(diff.time, `diff`, IOResult(_, Success(Done))) = readResult.requestNext()
    read.sendComplete()
    readResult.expectComplete()

    // Rewrite error
    val rewriteResult = Source.single(TestUtils.randomBytes(200))
      .runWith(testRepository.write(diff.time))

    whenReady(rewriteResult) { result ⇒
      result.count shouldBe 0L
      result.status.isFailure shouldBe true
    }
  }

  "In-memory repository" should "store diff" in {
    testRepository(IndexRepository.inMemory)
  }

  "File repository" should "store diff" in {
    testRepository(IndexRepository.fromDirectory(Files.createTempDirectory("irp-test")))
  }

  it should "validate path" in {
    intercept[IllegalArgumentException](IndexRepository.fromDirectory(Files.createTempFile("irp-test", "file")))
  }
}
