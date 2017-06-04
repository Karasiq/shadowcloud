package com.karasiq.shadowcloud.test.actors

import java.nio.file.Files

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.pattern.ask
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestActorRef
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.actors._
import com.karasiq.shadowcloud.actors.RegionDispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexMerger, IndexRepositoryStreams}
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}

// Uses local filesystem
class RegionDispatcherTest extends ActorSpec with FlatSpecLike {
  val chunk = TestUtils.testChunk
  val folder = TestUtils.randomFolder()
  val folderDiff = FolderIndexDiff.create(folder)
  val indexRepository = Repository.forIndex(Repositories.fromDirectory(Files.createTempDirectory("vrt-index")))
  val chunksDir = Files.createTempDirectory("vrt-chunks")
  val fileRepository = Repository.forChunks(Repositories.fromDirectory(chunksDir))
  val index = system.actorOf(IndexDispatcher.props("testStorage", indexRepository), "index")
  val chunkIO = TestActorRef(ChunkIODispatcher.props(fileRepository), "chunkIO")
  val healthProvider = StorageHealthProviders.fromDirectory(chunksDir)
  val initialHealth = healthProvider.health.futureValue
  val storage = TestActorRef(StorageDispatcher.props("testStorage", index, chunkIO, healthProvider), "storage")
  val testRegion = TestActorRef(RegionDispatcher.props("testRegion", TestUtils.regionConfig("testRegion")), "testRegion")

  "Virtual region" should "register storage" in {
    testRegion ! RegionDispatcher.Register("testStorage", storage, initialHealth)
    expectNoMsg(100 millis)
  }

  it should "write chunk" in {
    storageSubscribe()

    // Write chunk
    val result = testRegion ? WriteChunk(chunk)
    result.futureValue shouldBe WriteChunk.Success(chunk, chunk)
    expectMsg(StorageEnvelope("testStorage", StorageEvents.ChunkWritten(ChunkPath("testRegion", TestUtils.storageConfig("testStorage").chunkKey(chunk)), chunk)))

    // Health update
    val StorageEnvelope("testStorage", StorageEvents.HealthUpdated(health)) = receiveOne(1 second)
    health.totalSpace shouldBe initialHealth.totalSpace
    health.usedSpace shouldBe (initialHealth.usedSpace + chunk.checksum.encryptedSize)
    health.canWrite shouldBe (initialHealth.canWrite - chunk.checksum.encryptedSize)

    // Chunk index update
    val StorageEnvelope("testStorage", StorageEvents.PendingIndexUpdated("testRegion", diff)) = receiveOne(1 second)
    diff.folders shouldBe empty
    diff.time should be > TestUtils.testTimestamp
    diff.chunks.newChunks shouldBe Set(chunk)
    diff.chunks.deletedChunks shouldBe empty

    expectNoMsg(1 second)
    val storedChunks = fileRepository.subRepository("testRegion").keys.runWith(TestSink.probe)
    storedChunks.requestNext(chunk.checksum.hash)
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
    val wrongChunk = chunk.copy(encryption = TestUtils.aesEncryption.createParameters(), data = chunk.data.copy(encrypted = TestUtils.randomBytes(chunk.data.plain.length)))
    wrongChunk shouldNot be (chunk)
    val result = testRegion ? WriteChunk(wrongChunk)
    result.futureValue shouldBe WriteChunk.Success(chunk, chunk)
  }

  it should "add folder" in {
    val diff = FolderIndexDiff.create(folder)
    testRegion ! RegionDispatcher.WriteIndex(diff)
    val RegionDispatcher.WriteIndex.Success(`diff`, result) = receiveOne(1 second)
    result.time shouldBe > (TestUtils.testTimestamp)
    result.folders shouldBe folderDiff
    result.chunks.newChunks shouldBe Set(chunk)
    result.chunks.deletedChunks shouldBe empty
  }

  it should "write index" in {
    storageSubscribe()
    testRegion ! RegionDispatcher.Synchronize
    val StorageEnvelope("testStorage", StorageEvents.IndexUpdated("testRegion", sequenceNr, diff, remote)) = receiveOne(5 seconds)
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
    val streams = IndexRepositoryStreams(TestUtils.storageConfig("testStorage"), system)
    val regionRepo = indexRepository.subRepository("testRegion")

    // Write #2
    val remoteDiff = TestUtils.randomDiff
    val (sideWrite, sideWriteResult) = TestSource.probe[(Long, IndexData)]
      .via(streams.write(regionRepo))
      .toMat(TestSink.probe)(Keep.both)
      .run()
    sideWrite.sendNext((2, IndexData("testRegion", 2, remoteDiff)))
    sideWrite.sendComplete()
    val IndexIOResult(2, IndexData("testRegion", 2, `remoteDiff`), StorageIOResult.Success(_, _)) = sideWriteResult.requestNext()
    sideWriteResult.expectComplete()

    // Synchronize
    storageSubscribe()
    testRegion ! RegionDispatcher.Synchronize
    val StorageEnvelope("testStorage", StorageEvents.IndexUpdated("testRegion", 2, `remoteDiff`, true)) = receiveOne(5 seconds)
    expectNoMsg(1 second)
    
    // Verify
    storage ! IndexDispatcher.GetIndex("testRegion")
    val IndexDispatcher.GetIndex.Success(_, IndexMerger.State(Seq((1, firstDiff), (2, `remoteDiff`)), IndexDiff.empty)) = receiveOne(1 second)
    firstDiff.folders shouldBe folderDiff
    firstDiff.chunks.newChunks shouldBe Set(chunk)
    firstDiff.chunks.deletedChunks shouldBe empty

    // Delete #1
    whenReady(regionRepo.delete(1)) { deleteResult ⇒
      deleteResult.isSuccess shouldBe true
      testRegion ! RegionDispatcher.Synchronize
      val StorageEnvelope("testStorage", StorageEvents.IndexDeleted("testRegion", sequenceNrs)) = receiveOne(5 seconds)
      sequenceNrs shouldBe Set[Long](1)
      storage ! IndexDispatcher.GetIndex("testRegion")
      val IndexDispatcher.GetIndex.Success(_, IndexMerger.State(Seq((2, `remoteDiff`)), IndexDiff.empty)) = receiveOne(1 second)
      expectNoMsg(1 second)
      testRegion ! RegionDispatcher.GetIndex
      val RegionDispatcher.GetIndex.Success(_, IndexMerger.State(Seq((RegionKey(_, "testStorage", 2), `remoteDiff`)), _)) = receiveOne(1 second)
    }

    storageUnsubscribe()
  }

  it should "compact index" in {
    // Read
    storage ! IndexDispatcher.GetIndex("testRegion")
    val IndexDispatcher.GetIndex.Success(_, IndexMerger.State(Seq((2, oldDiff)), IndexDiff.empty)) = receiveOne(1 second)

    // Write diff #3
    val newDiff = TestUtils.randomDiff.folders
    testRegion ! RegionDispatcher.WriteIndex(newDiff)
    val RegionDispatcher.WriteIndex.Success(`newDiff`, _) = receiveOne(1 second)

    // Compact
    storage ! IndexDispatcher.CompactIndex("testRegion")
    storage ! IndexDispatcher.Synchronize
    expectNoMsg(3 second)

    // Verify
    storage ! IndexDispatcher.GetIndexes
    val IndexDispatcher.GetIndexes.Success("testStorage", states) = receiveOne(1 seconds)
    states.keySet shouldBe Set("testRegion")
    val IndexMerger.State(Seq((4, resultDiff)), IndexDiff.empty) = states("testRegion")
    resultDiff.folders shouldBe oldDiff.folders.merge(newDiff)
    resultDiff.chunks shouldBe oldDiff.chunks
    resultDiff.time should be > oldDiff.time
  }

  private def storageUnsubscribe() = {
    sc.eventStreams.storage.unsubscribe(testActor)
  }

  private def storageSubscribe(): Unit = {
    sc.eventStreams.storage.subscribe(testActor, "testStorage")
  }
}
