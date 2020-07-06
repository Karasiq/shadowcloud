package com.karasiq.shadowcloud.drive.test

import akka.pattern.ask
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestActorRef
import akka.util.ByteString
import com.karasiq.shadowcloud.actors.RegionSupervisor.{CreateRegion, CreateStorage, RegisterStorage}
import com.karasiq.shadowcloud.drive.FileIOScheduler
import com.karasiq.shadowcloud.drive.FileIOScheduler._
import com.karasiq.shadowcloud.drive.config.SCDriveConfig
import com.karasiq.shadowcloud.drive.utils.ChunkPatch
import com.karasiq.shadowcloud.model.{Chunk, File}
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.karasiq.shadowcloud.test.utils.SCExtensionSpec
import org.scalatest.FlatSpecLike

import scala.concurrent.duration._

final class FileIOSchedulerTest extends SCExtensionSpec with FlatSpecLike {
  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  val config    = SCDriveConfig(sc.config.rootConfig.getConfig("drive"))
  val scheduler = TestActorRef[FileIOScheduler](FileIOScheduler.props(config, "testRegion", File("/123.txt")))
  val zeroes    = ByteString(0, 0, 0, 0, 0, 0, 0, 0, 0, 0).ensuring(_.length == 10)
  val testData  = ByteString("1234567890").ensuring(_.length == 10)

  // -----------------------------------------------------------------------
  // Tests
  // -----------------------------------------------------------------------
  "File IO scheduler" should "append data" in {
    scheduler ! WriteData(10L, testData)
    testChunks(_ shouldBe empty)
    testWrites(_ shouldBe Seq(ChunkPatch(10L, testData)))
    testRead(zeroes ++ testData)

    val flushResult = (scheduler ? Flush).mapTo[Flush.Success].futureValue.result
    flushResult.writes shouldBe Seq(ChunkPatch(10L, testData))
    flushResult.ops match {
      case ChunkIOOperation.ChunkAppended(ChunkRanges.Range(0, 20), chunk) +: Nil ⇒
        chunk.checksum.size shouldBe 20
    }

    testChunks { case chunk +: Nil ⇒ chunk.checksum.size shouldBe 20 }
    testRead(zeroes ++ testData)
  }

  it should "replace data" in {
    scheduler ! WriteData(0L, testData)
    testWrites(_ shouldBe Seq(ChunkPatch(0L, testData)))
    testRead(testData ++ testData)

    val flushResult = (scheduler ? Flush).mapTo[Flush.Success].futureValue.result
    flushResult.writes shouldBe Seq(ChunkPatch(0L, testData))
    flushResult.ops match {
      case ChunkIOOperation.ChunkRewritten(ChunkRanges.Range(0, 20), oldChunk, chunk) +: Nil ⇒
        oldChunk.checksum.size shouldBe 20
        chunk.checksum.size shouldBe 20
    }

    testChunks { case chunk +: Nil ⇒ chunk.checksum.size shouldBe 20 }
    testRead(testData ++ testData)
  }

  it should "replace and append" in {
    val write = WriteData(10L, zeroes ++ testData)
    scheduler ! write
    testWrites(_ shouldBe Seq(write: ChunkPatch))
    testRead(testData ++ zeroes ++ testData)

    val flushResult = (scheduler ? Flush).mapTo[Flush.Success].futureValue.result
    flushResult.writes shouldBe Seq(write: ChunkPatch)
    flushResult.ops.sortBy(_.range.start) match {
      case ChunkIOOperation.ChunkRewritten(ChunkRanges.Range(0, 20), oldChunk, chunk) +:
            ChunkIOOperation.ChunkAppended(ChunkRanges.Range(20, 30), newChunk) +: Nil ⇒
        oldChunk.checksum.size shouldBe 20
        chunk.checksum.size shouldBe 20
        newChunk.checksum.size shouldBe 10
    }

    testChunks {
      case chunk +: newChunk +: Nil ⇒
        chunk.checksum.size shouldBe 20
        newChunk.checksum.size shouldBe 10
    }

    testRead(testData ++ zeroes ++ testData)
  }

  it should "optimize append" in {
    val write = WriteData(0L, testData ++ zeroes ++ testData ++ testData)
    scheduler ! write
    testWrites(_ shouldBe Seq(write: ChunkPatch))
    testRead(testData ++ zeroes ++ testData ++ testData)

    val flushResult = (scheduler ? Flush).mapTo[Flush.Success].futureValue.result
    flushResult.writes shouldBe Seq(write: ChunkPatch)
    flushResult.ops.sortBy(_.range.start) match {
      case ChunkIOOperation.ChunkAppended(ChunkRanges.Range(0, 40), newChunk) +: Nil ⇒
        newChunk.checksum.size shouldBe 40
    }

    testChunks {
      case newChunk +: Nil ⇒
        newChunk.checksum.size shouldBe 40
    }

    testRead(testData ++ zeroes ++ testData ++ testData)
  }

  it should "cut file" in {
    (scheduler ? CutFile(25)).futureValue
    testChunks(_ shouldBe empty)
    testRead(testData ++ zeroes ++ testData.take(5))

    val flushResult = (scheduler ? Flush).mapTo[Flush.Success].futureValue.result
    flushResult.writes shouldBe Seq(ChunkPatch(0, testData ++ zeroes ++ testData.take(5)))
    flushResult.ops match {
      case ChunkIOOperation.ChunkAppended(ChunkRanges.Range(0, 25), newChunk) +: Nil ⇒
        newChunk.checksum.size shouldBe 25
    }

    testChunks {
      case newChunk +: Nil ⇒
        newChunk.checksum.size shouldBe 25
    }
    testRead(testData ++ zeroes ++ testData.take(5))
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  def testRead(data: ByteString) = {
    val testSink = scheduler.underlyingActor.dataIO
      .readStream(0 until 100)
      .via(ByteStreams.concat)
      .runWith(TestSink.probe)

    testSink.request(2)
    testSink.expectNext(data)
    testSink.expectComplete()

    val testSink1 = scheduler.underlyingActor.dataIO
      .readStream(0 until 5)
      .via(ByteStreams.concat)
      .runWith(TestSink.probe)

    testSink1.request(2)
    testSink1.expectNext(data.take(5))
    testSink1.expectComplete()
  }

  def testChunks(f: Seq[Chunk] ⇒ Unit) = {
    f(scheduler.underlyingActor.dataState.currentChunks.values.toList)
  }

  def testWrites(f: Seq[ChunkPatch] ⇒ Unit) = {
    f(scheduler.underlyingActor.dataState.pendingWrites)
  }

  // -----------------------------------------------------------------------
  // Misc
  // -----------------------------------------------------------------------
  override protected def beforeAll(): Unit = {
    super.beforeAll()
    sc.actors.regionSupervisor ! CreateRegion("testRegion", sc.configs.regionConfig("testRegion"))
    sc.actors.regionSupervisor ! CreateStorage("testStorage", StorageProps.inMemory)
    sc.actors.regionSupervisor ! RegisterStorage("testRegion", "testStorage")
    awaitAssert(sc.ops.region.getHealth("testRegion").futureValue shouldBe 'fullyOnline, 10 seconds)
  }
}
