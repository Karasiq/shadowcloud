package com.karasiq.shadowcloud.test.actors

import java.nio.file.Files

import akka.Done
import akka.pattern.ask
import akka.stream.IOResult
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestActorRef
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors._
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.crypto.EncryptionMethod
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexRepositoryStreams}
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}
import org.scalatest.FlatSpecLike

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Success

// Uses local filesystem
class RegionTest extends ActorSpec with FlatSpecLike {
  val chunk = TestUtils.testChunk
  val folder = TestUtils.randomFolder()
  val folderDiff = FolderIndexDiff.create(folder)
  val indexRepository = IndexRepository.fromDirectory(Files.createTempDirectory("vrt-index"))
  val chunksDir = Files.createTempDirectory("vrt-chunks")
  val fileRepository = ChunkRepository.fromDirectory(chunksDir)
  val index = TestActorRef(IndexDispatcher.props("testStorage", indexRepository), "index")
  val chunkIO = TestActorRef(ChunkIODispatcher.props(fileRepository), "chunkIO")
  val healthProvider = StorageHealthProvider.fromDirectory(chunksDir)
  val initialHealth = healthProvider.health.futureValue
  val storage = TestActorRef(StorageDispatcher.props("testStorage", index, chunkIO, healthProvider), "storage")
  val testRegion = TestActorRef(RegionDispatcher.props("testRegion"), "testRegion")

  "Virtual region" should "register storage" in {
    testRegion ! RegionDispatcher.Register("testStorage", storage, initialHealth)
    expectNoMsg(100 millis)
  }

  it should "write chunk" in {
    storageSubscribe()

    // Write chunk
    val result = testRegion ? WriteChunk(chunk)
    result.futureValue shouldBe WriteChunk.Success(chunk, chunk)
    expectMsg(StorageEnvelope("testStorage", StorageEvents.ChunkWritten(chunk)))

    // Health update
    val StorageEnvelope("testStorage", StorageEvents.HealthUpdated(health)) = receiveOne(1 second)
    health.totalSpace shouldBe initialHealth.totalSpace
    health.usedSpace shouldBe (initialHealth.usedSpace + chunk.checksum.encryptedSize)
    health.canWrite shouldBe (initialHealth.canWrite - chunk.checksum.encryptedSize)

    // Chunk index update
    val StorageEnvelope("testStorage", StorageEvents.PendingIndexUpdated(diff)) = receiveOne(1 second)
    diff.folders shouldBe empty
    diff.time should be > TestUtils.testTimestamp
    diff.chunks.newChunks shouldBe Set(chunk)
    diff.chunks.deletedChunks shouldBe empty

    expectNoMsg(1 second)
    val storedChunks = fileRepository.chunks.runWith(TestSink.probe)
    storedChunks.requestNext(chunk.checksum.hash.toHexString)
    storedChunks.expectComplete()
    storageUnsubscribe()
  }

  it should "read chunk" in {
    testRegion ! ReadChunk(chunk.withoutData)
    val ReadChunk.Success(_, result) = receiveOne(1 second)
    result shouldBe chunk
    result.data.encrypted shouldBe chunk.data.encrypted
  }

  it should "deduplicate chunk" in {
    val wrongChunk = chunk.copy(encryption = chunk.encryption.copy(EncryptionMethod.AES()), data = chunk.data.copy(encrypted = TestUtils.randomBytes(chunk.data.plain.length)))
    wrongChunk shouldNot be (chunk)
    val result = testRegion ? WriteChunk(wrongChunk)
    result.futureValue shouldBe WriteChunk.Success(chunk, chunk)
  }

  it should "add folder" in {
    testRegion ! RegionDispatcher.WriteIndex(FolderIndexDiff.create(folder))
    val RegionDispatcher.WriteIndex.Success(diff, result) = receiveOne(1 second)
    diff.time shouldBe > (TestUtils.testTimestamp)
    diff.folders shouldBe folderDiff
    diff.chunks.newChunks shouldBe empty
    diff.chunks.deletedChunks shouldBe empty
    result.time shouldBe > (TestUtils.testTimestamp)
    result.folders shouldBe folderDiff
    result.chunks.newChunks shouldBe Set(chunk)
    result.chunks.deletedChunks shouldBe empty
  }

  it should "write index" in {
    storageSubscribe()
    testRegion ! RegionDispatcher.Synchronize
    val StorageEnvelope("testStorage", StorageEvents.IndexUpdated(sequenceNr, diff, remote)) = receiveOne(5 seconds)
    sequenceNr shouldBe 1
    diff.time shouldBe > (TestUtils.testTimestamp)
    diff.folders shouldBe folderDiff
    diff.chunks.newChunks shouldBe Set(chunk)
    diff.chunks.deletedChunks shouldBe empty
    remote shouldBe false
    expectNoMsg(1 second)
    storageUnsubscribe()
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
    val IndexIOResult("2", `diff1`, IOResult(_, Success(Done))) = sideWriteResult.requestNext()
    sideWriteResult.expectComplete()
    storageSubscribe()
    testRegion ! RegionDispatcher.Synchronize
    val StorageEnvelope("testStorage", StorageEvents.IndexUpdated(sequenceNr, diff, remote)) = receiveOne(5 seconds)
    sequenceNr shouldBe 2
    diff shouldBe diff1
    remote shouldBe true
    expectNoMsg(1 second)
    storage ! IndexDispatcher.GetIndex
    val IndexDispatcher.GetIndex.Success(Seq((1, firstDiff), (2, secondDiff))) = receiveOne(1 second)
    firstDiff.folders shouldBe folderDiff
    firstDiff.chunks.newChunks shouldBe Set(chunk)
    firstDiff.chunks.deletedChunks shouldBe empty
    secondDiff shouldBe diff1
    storageUnsubscribe()
  }

  private def storageUnsubscribe() = {
    StorageEvents.stream.unsubscribe(testActor)
  }

  private def storageSubscribe(): Unit = {
    StorageEvents.stream.subscribe(testActor, "testStorage")
  }
}
