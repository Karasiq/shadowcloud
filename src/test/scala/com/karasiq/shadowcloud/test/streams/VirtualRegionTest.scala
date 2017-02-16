package com.karasiq.shadowcloud.test.streams

import java.nio.file.Files

import akka.pattern.ask
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestActorRef
import akka.util.ByteString
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.events.StorageEvent
import com.karasiq.shadowcloud.actors.events.StorageEvent.StorageEnvelope
import com.karasiq.shadowcloud.actors.{IndexSynchronizer, _}
import com.karasiq.shadowcloud.crypto.EncryptionMethod
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.{ChunkRepository, IndexRepository, IndexRepositoryStreams}
import com.karasiq.shadowcloud.test.utils.TestUtils.ByteStringOps
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}
import org.scalatest.FlatSpecLike

import scala.concurrent.duration._
import scala.language.postfixOps

// Uses local filesystem
class VirtualRegionTest extends ActorSpec with FlatSpecLike {
  val chunk = TestUtils.testChunk
  val indexRepository = IndexRepository.fromDirectory(Files.createTempDirectory("vrt-index"))
  val fileRepository = ChunkRepository.fromDirectory(Files.createTempDirectory("vrt-chunks"))
  val index = TestActorRef(IndexSynchronizer.props("testStorage", indexRepository), "index")
  val chunkIO = TestActorRef(ChunkIODispatcher.props(fileRepository), "chunkIO")
  val storage = TestActorRef(StorageDispatcher.props("testStorage", index, chunkIO), "storage")
  val testRegion = TestActorRef(VirtualRegionDispatcher.props("testRegion"), "testRegion")

  "Virtual region" should "register storage" in {
    testRegion ! VirtualRegionDispatcher.Register("testStorage", storage)
    expectNoMsg(100 millis)
  }

  it should "write chunk" in {
    StorageEvent.stream.subscribe(testActor, "testStorage")
    val result = testRegion ? WriteChunk(chunk)
    result.futureValue shouldBe WriteChunk.Success(chunk, chunk)
    expectMsg(StorageEnvelope("testStorage", StorageEvent.ChunkWritten(chunk)))
    val StorageEnvelope("testStorage", StorageEvent.PendingIndexUpdated(diff)) = receiveOne(1 second)
    diff.folders shouldBe empty
    diff.time should be > TestUtils.testTimestamp
    diff.chunks.newChunks shouldBe Set(chunk)
    diff.chunks.deletedChunks shouldBe empty
    expectNoMsg(1 second)
    val storedChunks = fileRepository.chunks.runWith(TestSink.probe)
    storedChunks.requestNext(chunk.checksum.hash.toHexString)
    storedChunks.expectComplete()
    StorageEvent.stream.unsubscribe(testActor, "testStorage")
  }

  it should "read chunk" in {
    val future = testRegion ? ReadChunk(chunk.withoutData)
    whenReady(future) {
      case ReadChunk.Success(_, source) ⇒
        val probe = source
          .fold(ByteString.empty)(_ ++ _)
          .runWith(TestSink.probe)
        probe.requestNext(chunk.data.encrypted)
        probe.expectComplete()
    }
  }

  it should "deduplicate chunk" in {
    val wrongChunk = chunk.copy(encryption = chunk.encryption.copy(EncryptionMethod.AES()), data = chunk.data.copy(encrypted = TestUtils.randomBytes(chunk.data.plain.length)))
    wrongChunk shouldNot be (chunk)
    val result = testRegion ? WriteChunk(wrongChunk)
    result.futureValue shouldBe WriteChunk.Success(chunk, chunk)
  }

  it should "write index" in {
    StorageEvent.stream.subscribe(testActor, "testStorage")
    index ! IndexSynchronizer.Synchronize
    val StorageEnvelope("testStorage", StorageEvent.IndexUpdated(sequenceNr, diff, remote)) = receiveOne(5 seconds)
    sequenceNr shouldBe 1
    diff.time shouldBe > (TestUtils.testTimestamp)
    diff.folders shouldBe empty
    diff.chunks.newChunks shouldBe Set(chunk)
    diff.chunks.deletedChunks shouldBe empty
    remote shouldBe false
    expectNoMsg(1 second)
    StorageEvent.stream.unsubscribe(testActor)
  }

  it should "read index" in {
    val streams = IndexRepositoryStreams.gzipped
    val (sideWrite, sideWriteResult) = TestSource.probe[(String, IndexDiff)]
      .via(streams.write(indexRepository))
      .toMat(TestSink.probe)(Keep.both)
      .run()
    val diff1 = TestUtils.randomDiff
    sideWrite.sendNext((2.toString, diff1))
    sideWrite.sendComplete()
    sideWriteResult.requestNext((2.toString, diff1))
    sideWriteResult.expectComplete()
    StorageEvent.stream.subscribe(testActor, "testStorage")
    index ! IndexSynchronizer.Synchronize
    val StorageEnvelope("testStorage", StorageEvent.IndexUpdated(sequenceNr, diff, remote)) = receiveOne(5 seconds)
    sequenceNr shouldBe 2
    diff shouldBe diff1
    remote shouldBe true
    expectNoMsg(1 second)
    storage ! IndexSynchronizer.GetIndex
    val IndexSynchronizer.GetIndex.Success(Seq((1, firstDiff), (2, secondDiff))) = receiveOne(1 second)
    firstDiff.folders shouldBe empty
    firstDiff.chunks.newChunks shouldBe Set(chunk)
    firstDiff.chunks.deletedChunks shouldBe empty
    secondDiff shouldBe diff1
    StorageEvent.stream.unsubscribe(testActor)
  }
}
