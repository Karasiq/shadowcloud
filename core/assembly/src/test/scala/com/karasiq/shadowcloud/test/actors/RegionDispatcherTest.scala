package com.karasiq.shadowcloud.test.actors

import java.nio.file.Files

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.PoisonPill
import akka.pattern.ask
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.actors._
import com.karasiq.shadowcloud.actors.RegionDispatcher.{GetFileAvailability, ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.actors.events.StorageEvents.HealthUpdated
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.props.StorageProps.Quota
import com.karasiq.shadowcloud.storage.replication.ChunkWriteAffinity
import com.karasiq.shadowcloud.storage.repository.{PathTreeRepository, Repository}
import com.karasiq.shadowcloud.storage.repository.wrappers.PathNodesMapper
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexMerger, IndexRepositoryStreams}
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.test.utils.{CoreTestUtils, SCExtensionSpec, TestUtils}
import com.karasiq.shadowcloud.utils.encoding.{Base64, HexString}

// Uses local filesystem
class RegionDispatcherTest extends SCExtensionSpec with FlatSpecLike {
  val chunk = TestUtils.testChunk
  val folder = CoreTestUtils.randomFolder()
  val folderDiff = FolderIndexDiff.createFolders(folder)
  val indexRepository = Repository.forIndex(PathTreeRepository.toCategorized(
    PathNodesMapper.encode(Repositories.fromDirectory(Files.createTempDirectory("vrt-index")), Base64)))
  val chunksDir = Files.createTempDirectory("vrt-chunks")
  val fileRepository = Repository.forChunks(PathTreeRepository.toCategorized(Repositories.fromDirectory(chunksDir)))
  val storageProps = StorageProps.fromDirectory(chunksDir.getParent)
  val index = system.actorOf(StorageIndex.props("testStorage", storageProps, indexRepository), "index")
  val chunkIO = system.actorOf(ChunkIODispatcher.props("testStorage", storageProps, fileRepository), "chunkIO")
  val healthProvider = StorageHealthProviders.fromDirectory(chunksDir, Quota.empty.copy(limitSpace = Some(100L * 1024 * 1024)))
  val initialHealth = healthProvider.health.futureValue
  val storage = system.actorOf(StorageDispatcher.props("testStorage", storageProps, index, chunkIO, healthProvider), "storage")
  val testRegion = system.actorOf(RegionDispatcher.props("testRegion", CoreTestUtils.regionConfig("testRegion")), "testRegion")

  "Virtual region" should "register storage" in {
    storage ! StorageIndex.OpenIndex("testRegion")
    testRegion ! RegionDispatcher.AttachStorage("testStorage", storageProps, storage, initialHealth)
    expectNoMsg(1 second)
  }

  it should "write chunk" in {
    storageSubscribe()

    // Write chunk
    val result = testRegion ? WriteChunk(chunk)
    result.futureValue shouldBe WriteChunk.Success(chunk, chunk)

    receiveN(2).map(_.asInstanceOf[StorageEnvelope]).map(_.message).sortBy(_.isInstanceOf[HealthUpdated]) match {
      case StorageEvents.ChunkWritten(ChunkPath("testRegion", chunk.checksum.hash), chunk) +: StorageEvents.HealthUpdated(health) +: Nil ⇒
        health.totalSpace shouldBe initialHealth.totalSpace
        health.usedSpace shouldBe (initialHealth.usedSpace + chunk.checksum.encSize)
        health.writableSpace shouldBe (initialHealth.writableSpace - chunk.checksum.encSize)
    }

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
    val wrongChunk = chunk.copy(encryption = CoreTestUtils.aesEncryption.createParameters(), data = chunk.data.copy(encrypted = TestUtils.randomBytes(chunk.data.plain.length)))
    wrongChunk shouldNot be(chunk)
    val result = testRegion ? WriteChunk(wrongChunk)
    result.futureValue shouldBe WriteChunk.Success(chunk, chunk)
  }

  it should "create availability report" in {
    val report = (testRegion ? GetFileAvailability(folder.files.head.copy(chunks = Seq(chunk)))).mapTo[GetFileAvailability.Success].futureValue
    report.result.chunksByStorage shouldBe Map("testStorage" → Set(chunk))
    report.result.percentagesByStorage shouldBe Map("testStorage" → 100.0)
  }

  it should "repair chunk" in {
    // Create new storage
    val indexMap = TrieMap.empty[(String, String), ByteString]
    val chunksMap = TrieMap.empty[(String, String), ByteString]
    val indexRepository = Repository.forIndex(Repository.toCategorized(Repositories.fromConcurrentMap(indexMap)))
    val chunkRepository = Repository.forChunks(Repository.toCategorized(Repositories.fromConcurrentMap(chunksMap)))
    val index = system.actorOf(StorageIndex.props("testMemStorage", storageProps, indexRepository), "index1")
    val chunkIO = system.actorOf(ChunkIODispatcher.props("testMemStorage", storageProps, chunkRepository), "chunkIO1")
    val healthProvider = StorageHealthProviders.fromMaps(indexMap, chunksMap)
    val initialHealth = healthProvider.health.futureValue
    val newStorage = system.actorOf(StorageDispatcher.props("testMemStorage", storageProps, index, chunkIO, healthProvider), "storage1")
    testRegion ! RegionDispatcher.AttachStorage("testMemStorage", storageProps, newStorage, initialHealth)
    expectNoMsg(1 second)

    // Replicate chunk
    val result = testRegion ? RegionDispatcher.RewriteChunk(chunk, Some(ChunkWriteAffinity(mandatory = Seq("testStorage", "testMemStorage"))))
    result.futureValue shouldBe WriteChunk.Success(chunk, chunk)
    chunksMap.head shouldBe (("testRegion", HexString.encode(chunk.checksum.hash)), chunk.data.encrypted)

    // Drop storage
    testRegion ! RegionDispatcher.DetachStorage("testMemStorage")
    newStorage ! PoisonPill
    expectNoMsg(1 second)
  }

  it should "add folder" in {
    storageSubscribe()
    val diff = FolderIndexDiff.createFolders(folder)
    testRegion ! RegionDispatcher.WriteIndex(diff)
    receiveWhile(5 seconds) {
      case RegionDispatcher.WriteIndex.Success(`diff`, result) ⇒
        result.time shouldBe >(TestUtils.testTimestamp)
        result.folders.folders.toSet shouldBe folderDiff.folders.toSet
        // result.chunks.newChunks shouldBe Set(chunk)
        result.chunks.deletedChunks shouldBe empty

      case StorageEnvelope(storageId, StorageEvents.PendingIndexUpdated(regionId, diff)) ⇒
        storageId shouldBe "testStorage"
        regionId shouldBe "testRegion"
        diff.folders.folders.toSet shouldBe folderDiff.folders.toSet
    }
  }

  it should "write index" in {
    (testRegion ? RegionDispatcher.Synchronize).futureValue
    val StorageEnvelope("testStorage", StorageEvents.IndexUpdated("testRegion", sequenceNr, diff, remote)) = receiveOne(5 seconds)
    sequenceNr shouldBe 1L
    diff.time shouldBe >(TestUtils.testTimestamp)
    diff.folders.folders.toSet shouldBe folderDiff.folders.toSet
    diff.chunks.newChunks shouldBe Set(chunk)
    diff.chunks.deletedChunks shouldBe empty
    remote shouldBe false
    expectNoMsg(1 second)
    storageUnsubscribe()
  }

  it should "read index" in {
    val streams = IndexRepositoryStreams(CoreTestUtils.storageConfig("testStorage"), system)
    val regionRepo = indexRepository.subRepository("testRegion")

    // Write #2
    val remoteDiff = CoreTestUtils.randomDiff
    val (sideWrite, sideWriteResult) = TestSource.probe[(Long, IndexData)]
      .via(streams.write(regionRepo))
      .toMat(TestSink.probe)(Keep.both)
      .run()
    sideWrite.sendNext((2, IndexData("testRegion", 2L, remoteDiff)))
    sideWrite.sendComplete()
    val IndexIOResult(2, IndexData("testRegion", 2L, `remoteDiff`), StorageIOResult.Success(_, _)) = sideWriteResult.requestNext()
    sideWriteResult.expectComplete()

    // Synchronize
    storageSubscribe()
    (testRegion ? RegionDispatcher.Synchronize).futureValue
    val StorageEnvelope("testStorage", StorageEvents.IndexUpdated("testRegion", 2L, `remoteDiff`, true)) = receiveOne(5 seconds)
    expectNoMsg(1 second)

    // Verify
    storage ! StorageIndex.Envelope("testRegion", RegionIndex.GetIndex)
    val RegionIndex.GetIndex.Success(_, IndexMerger.State(Seq((1L, firstDiff), (2L, `remoteDiff`)), IndexDiff.empty)) = receiveOne(1 second)
    firstDiff.folders shouldBe folderDiff
    firstDiff.chunks.newChunks shouldBe Set(chunk)
    firstDiff.chunks.deletedChunks shouldBe empty

    // Delete #1
    whenReady(Source.single(1L).runWith(regionRepo.delete)) { deleteResult ⇒
      deleteResult.isSuccess shouldBe true
      (testRegion ? RegionDispatcher.Synchronize).futureValue
      val StorageEnvelope("testStorage", StorageEvents.IndexDeleted("testRegion", sequenceNrs)) = receiveOne(5 seconds)
      sequenceNrs shouldBe Set(1L)
      storage ! StorageIndex.Envelope("testRegion", RegionIndex.GetIndex)
      val RegionIndex.GetIndex.Success(_, IndexMerger.State(Seq((2L, `remoteDiff`)), IndexDiff.empty)) = receiveOne(1 second)
      expectNoMsg(1 second)
      testRegion ! RegionDispatcher.GetIndexSnapshot()
      val RegionDispatcher.GetIndexSnapshot.Success(_, IndexMerger.State(Seq((RegionKey(_, "testStorage", 2L), `remoteDiff`)), _)) = receiveOne(1 second)
    }

    storageUnsubscribe()
  }

  it should "compact index" in {
    // Read
    storage ! StorageIndex.Envelope("testRegion", RegionIndex.GetIndex)
    val RegionIndex.GetIndex.Success(_, IndexMerger.State(Seq((2L, oldDiff)), IndexDiff.empty)) = receiveOne(1 second)

    // Write diff #3
    val newDiff = CoreTestUtils.randomDiff.folders
    testRegion ! RegionDispatcher.WriteIndex(newDiff)
    val RegionDispatcher.WriteIndex.Success(`newDiff`, _) = receiveOne(1 second)

    // Compact
    storage ! StorageIndex.Envelope("testRegion", RegionIndex.Compact)
    (storage ? StorageIndex.Envelope("testRegion", RegionIndex.Synchronize)).futureValue
    expectNoMsg(1 second)

    // Verify
    storage ! StorageIndex.GetIndexes
    val StorageIndex.GetIndexes.Success("testStorage", states) = receiveOne(1 seconds)
    states.keySet shouldBe Set("testRegion")
    val IndexMerger.State(Seq((4L, resultDiff)), IndexDiff.empty) = states("testRegion")
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
