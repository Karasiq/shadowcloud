package com.karasiq.shadowcloud.test.storage

import java.nio.file.Files

import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.{BaseIndexRepository, IndexRepository, IndexRepositoryStreams}
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}
import org.scalatest.FlatSpecLike

import scala.language.postfixOps

class IndexRepositoryTest extends ActorSpec with FlatSpecLike {
  def testRepository(repository: BaseIndexRepository): Unit = {
    val diff = TestUtils.randomDiff
    val testRepository = IndexRepository.numeric(repository)
    val streams = IndexRepositoryStreams.gzipped
    val (write, writeResult) = TestSource.probe[(Long, IndexDiff)]
      .via(streams.write(testRepository))
      .toMat(TestSink.probe)(Keep.both)
      .run()
    write.sendNext((diff.time, diff))
    writeResult.requestNext((diff.time, diff))
    write.sendComplete()
    writeResult.expectComplete()

    val keys = testRepository.keys
      .runWith(TestSink.probe)
    keys.requestNext(diff.time)
    keys.expectComplete()

    val (read, readResult) = TestSource.probe[Long]
      .via(streams.read(testRepository))
      .toMat(TestSink.probe)(Keep.both)
      .run()
    read.sendNext(diff.time)
    readResult.requestNext((diff.time, diff))
    read.sendComplete()
    readResult.expectComplete()
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
