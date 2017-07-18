package com.karasiq.shadowcloud.test.actors

import scala.concurrent.duration._
import scala.language.postfixOps

import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.actors.RegionRepairDispatcher
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.actors.RegionRepairDispatcher.Repair
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.replication.ChunkWriteAffinity
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}

class RegionRepairTest extends ActorSpec with FlatSpecLike {
  val testRegionId = "repairTestRegion"
  val testStorage1 = "repairTestS1"
  val testStorage2 = "repairTestS2"

  "Region repair dispatcher" should "repair chunks" in {
    registerRegionAndStorages()
    expectNoMsg(100 millis) // Wait for registration

    val chunk = TestUtils.testChunk
    sc.ops.region.writeChunk(testRegionId, chunk).futureValue shouldBe chunk
    sc.ops.region.synchronize(testRegionId)
    
    expectNoMsg(500 millis) // Wait for synchronization

    val repairDispatcher = system.actorOf(RegionRepairDispatcher.props(testRegionId))
    repairDispatcher ! Repair(ChunkWriteAffinity(mandatory = Seq(testStorage1, testStorage2)))
    val Repair.Success(_, chunks) = receiveOne(500 millis)
    chunks shouldBe Seq(chunk)

    val chunkPath = ChunkPath(testRegionId, chunk.checksum.hash)
    sc.ops.storage.readChunk(testStorage1, chunkPath, chunk).futureValue shouldBe chunk
    sc.ops.storage.readChunk(testStorage2, chunkPath, chunk).futureValue shouldBe chunk
  }

  private[this] def registerRegionAndStorages(): Unit = {
    sc.ops.supervisor.addRegion(testRegionId, sc.regionConfig(testRegionId))
    sc.ops.supervisor.addStorage(testStorage1, StorageProps.inMemory)
    sc.ops.supervisor.addStorage(testStorage2, StorageProps.inMemory)
    sc.ops.supervisor.register(testRegionId, testStorage1)
    sc.ops.supervisor.register(testRegionId, testStorage2)
  }
}
