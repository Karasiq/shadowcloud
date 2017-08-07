package com.karasiq.shadowcloud.test.actors

import scala.concurrent.duration._
import scala.language.postfixOps

import org.scalatest.FlatSpecLike
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.test.utils.{CoreTestUtils, SCExtensionSpec}

class RegionGCTest extends SCExtensionSpec with FlatSpecLike {
  val testRegionId = "regionGCTest"
  val testStorageId = "regionGCTest"

  "Region GC" should "delete broken chunks" in {
    // registerRegionAndStorages()

    val chunk = CoreTestUtils.randomChunk
    sc.ops.region.writeChunk(testRegionId, chunk).futureValue shouldBe chunk
    sc.ops.region.synchronize(testRegionId)
    expectNoMsg(1 seconds)
    sc.ops.storage.deleteChunks(testStorageId, Set(ChunkPath(testRegionId, chunk.checksum.hash))).futureValue._2.isSuccess shouldBe true
    whenReady(sc.ops.region.collectGarbage(testRegionId, delete = true), Timeout(10 seconds)) { gcReport ⇒
      gcReport.regionId shouldBe testRegionId
      gcReport.regionState.oldFiles shouldBe empty
      gcReport.regionState.orphanedChunks shouldBe Set(chunk)
      gcReport.regionState.expiredMetadata shouldBe empty

      val Seq((`testStorageId`, storageState)) = gcReport.storageStates.toSeq
      storageState.notExisting shouldBe Set(chunk)
      storageState.notIndexed shouldBe empty
    }
  }

  it should "delete unindexed chunks" in {
    val chunk = CoreTestUtils.randomChunk
    sc.ops.region.writeChunk(testRegionId, chunk).futureValue shouldBe chunk
    sc.ops.region.synchronize(testRegionId)
    expectNoMsg(1 seconds)
    
    sc.ops.storage.writeIndex(testStorageId, testRegionId, IndexDiff.deleteChunks(chunk)).futureValue
    sc.ops.storage.synchronize(testStorageId, testRegionId)
    expectNoMsg(1 seconds)

    whenReady(sc.ops.region.collectGarbage(testRegionId, delete = true), Timeout(10 seconds)) { gcReport ⇒
      gcReport.regionId shouldBe testRegionId
      gcReport.regionState.oldFiles shouldBe empty
      gcReport.regionState.orphanedChunks shouldBe empty
      gcReport.regionState.expiredMetadata shouldBe empty

      val Seq((`testStorageId`, storageState)) = gcReport.storageStates.toSeq
      storageState.notExisting shouldBe empty
      storageState.notIndexed shouldBe Set(chunk.checksum.hash)
    }
  }


  override protected def beforeAll(): Unit = {
    super.beforeAll()
    registerRegionAndStorages()
  }

  private[this] def registerRegionAndStorages(): Unit = {
    sc.ops.supervisor.addRegion(testRegionId, sc.configs.regionConfig(testRegionId))
    sc.ops.supervisor.addStorage(testStorageId, StorageProps.inMemory) // fromDirectory(Files.createTempDirectory("region-gc-test"))
    sc.ops.supervisor.register(testRegionId, testStorageId)
    expectNoMsg(300 millis)
  }
}
