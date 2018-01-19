package com.karasiq.shadowcloud.drive.test

import akka.pattern.ask
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestActorRef
import akka.util.ByteString
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.actors.RegionSupervisor.{CreateRegion, CreateStorage, RegisterStorage}
import com.karasiq.shadowcloud.drive.FileIOScheduler
import com.karasiq.shadowcloud.drive.config.SCDriveConfig
import com.karasiq.shadowcloud.drive.FileIOScheduler._
import com.karasiq.shadowcloud.model.{Chunk, File}
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.karasiq.shadowcloud.test.utils.SCExtensionSpec

class FileIOSchedulerTest extends SCExtensionSpec with FlatSpecLike {
  val config = SCDriveConfig(sc.config.rootConfig.getConfig("drive"))
  val scheduler = TestActorRef[FileIOScheduler](FileIOScheduler.props(config, "testRegion", File("/123.txt")))
  val zeroes = ByteString(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
  val testData = ByteString("1234567890")

  "File IO scheduler" should "append data" in {
    scheduler ! WriteData(10L, testData)
    scheduler.underlyingActor.currentChunks shouldBe empty
    scheduler.underlyingActor.pendingWrites shouldBe Seq(WriteData(10L, testData))
    testRead(zeroes ++ testData)

    val flushResult = (scheduler ? Flush).mapTo[Flush.Success].futureValue.result
    flushResult.writes shouldBe Seq(WriteData(10L, testData))
    flushResult.ops match {
      case IOOperation.ChunkAppended(ChunkRanges.Range(0, 20), chunk) +: Nil ⇒
        chunk.checksum.size shouldBe 20
    }

    testChunks { case chunk +: Nil ⇒ chunk.checksum.size shouldBe 20 }
    testRead(zeroes ++ testData)
  }

  it should "replace data" in {
    scheduler ! WriteData(0L, testData)
    scheduler.underlyingActor.pendingWrites shouldBe Seq(WriteData(0L, testData))
    testRead(testData ++ testData)

    val flushResult = (scheduler ? Flush).mapTo[Flush.Success].futureValue.result
    flushResult.writes shouldBe Seq(WriteData(0L, testData))
    flushResult.ops match {
      case IOOperation.ChunkRewritten(ChunkRanges.Range(0, 20), oldChunk, chunk) +: Nil ⇒
        oldChunk.checksum.size shouldBe 20
        chunk.checksum.size shouldBe 20
    }

    testChunks { case chunk +: Nil ⇒ chunk.checksum.size shouldBe 20 }
    testRead(testData ++ testData)
  }

  def testRead(data: ByteString) = {
    val testSink = scheduler.underlyingActor.readStream(0 to 100)
      .via(ByteStreams.concat)
      .runWith(TestSink.probe)

    testSink.request(2)
    testSink.expectNext(data)
    testSink.expectComplete()
  }

  def testChunks(f: Seq[Chunk] ⇒ Unit) = {
    f(scheduler.underlyingActor.currentChunks.values.toSeq)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    sc.actors.regionSupervisor ! CreateRegion("testRegion", sc.configs.regionConfig("testRegion"))
    sc.actors.regionSupervisor ! CreateStorage("testStorage", StorageProps.inMemory)
    sc.actors.regionSupervisor ! RegisterStorage("testRegion", "testStorage")
  }
}
