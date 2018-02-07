package com.karasiq.shadowcloud.test.streams

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.{FlatSpecLike, SequentialNestedSuiteExecution}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.karasiq.shadowcloud.test.utils.{SCExtensionSpec, TestUtils}

class RangedChunkStreamTest extends SCExtensionSpec with FlatSpecLike with SequentialNestedSuiteExecution {
  val testRegion = "testRegion"
  val (testBytes, testFile) = TestUtils.indexedBytes

  "Ranged chunk stream" should "be read" in {
    writeTestFile()

    val ranges = ChunkRanges.RangeList(
      ChunkRanges.Range(10, 100),
      ChunkRanges.Range(80, 150)
    )

    val result = sc.streams.file
      .readChunkStreamRanged(testRegion, testFile.chunks.map(_.withoutData), ranges)
      .via(ByteStreams.concat)
      .runWith(Sink.head)
      .futureValue(Timeout(5 seconds))

    result shouldBe ranges.slice(testBytes)
  }

  private[this] def writeTestFile(): Unit = {
    // Write file
    val testStorage = "testStorage"
    sc.ops.supervisor.createRegion(testRegion, sc.configs.regionConfig(testRegion))
    sc.ops.supervisor.createStorage(testStorage, StorageProps.inMemory)
    sc.ops.supervisor.register(testRegion, testStorage)
    expectNoMessage(3 seconds)

    Source(testFile.chunks.toVector)
      .map((testRegion, _))
      .via(sc.streams.region.writeChunks)
      .runWith(Sink.ignore)
      .futureValue(Timeout(10 seconds))
  }
}
