package com.karasiq.shadowcloud.test.actors

import com.karasiq.shadowcloud.actors.RegionSupervisor
import com.karasiq.shadowcloud.actors.internal.RegionTracker
import com.karasiq.shadowcloud.actors.internal.RegionTracker.{RegionStatus, StorageStatus}
import com.karasiq.shadowcloud.actors.utils.ActorState
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.test.utils.{CoreTestUtils, SCExtensionSpec}
import org.scalatest.{FlatSpecLike, Matchers}

import scala.concurrent.duration._

class RegionSupervisorTest extends SCExtensionSpec with FlatSpecLike with Matchers {
  import RegionSupervisor._

  val testRegion = "testRegion"
  val testStorage = "testStorage"
  val supervisor = system.actorOf(props, "supervisor")

  "Region supervisor" should "add region" in {
    supervisor ! CreateRegion(testRegion, CoreTestUtils.regionConfig("testRegion"))
    val (regions, storages) = requestState()
    regions.keySet shouldBe Set(testRegion)
    val region = regions(testRegion)
    region.storages shouldBe empty
    region.regionId shouldBe testRegion
    storages shouldBe empty
  }

  it should "add storage" in {
    supervisor ! CreateStorage(testStorage, StorageProps.inMemory)
    val (regions, storages) = requestState()
    regions.keySet shouldBe Set(testRegion)
    storages.keySet shouldBe Set(testStorage)
    val storage = storages(testStorage)
    storage.regions shouldBe empty
    storage.storageProps shouldBe StorageProps.inMemory
    storage.storageId shouldBe testStorage
  }

  it should "register storage" in {
    supervisor ! RegisterStorage(testRegion, testStorage)
    val (regions, storages) = requestState()
    regions.keySet shouldBe Set(testRegion)
    storages.keySet shouldBe Set(testStorage)
    regions(testRegion).storages shouldBe Set(testStorage)
    storages(testStorage).regions shouldBe Set(testRegion)
  }

  it should "unregister storage" in {
    supervisor ! UnregisterStorage(testRegion, testStorage)
    val (regions, storages) = requestState()
    regions.keySet shouldBe Set(testRegion)
    storages.keySet shouldBe Set(testStorage)
    regions(testRegion).storages shouldBe empty
    storages(testStorage).regions shouldBe empty
  }

  it should "delete storage" in {
    supervisor ! RegisterStorage(testRegion, testStorage)
    supervisor ! DeleteStorage(testStorage)
    val (regions, storages) = requestState()
    regions.keySet shouldBe Set(testRegion)
    storages shouldBe empty
    regions(testRegion).storages shouldBe empty
    expectNoMsg(500 millis) // Wait for storage actor stop
  }

  it should "suspend storage" in {
    supervisor ! CreateStorage(testStorage, StorageProps.inMemory)
    supervisor ! RegisterStorage(testRegion, testStorage)
    supervisor ! SuspendStorage(testStorage)
    val (regions, storages) = requestState()
    storages(testStorage) shouldBe StorageStatus(testStorage, StorageProps.inMemory, ActorState.Suspended, Set(testRegion))
    regions.keySet shouldBe Set(testRegion)
    regions(testRegion).storages shouldBe Set(testStorage)
  }

  it should "resume storage" in {
    supervisor ! ResumeStorage(testStorage)
    val (regions, storages) = requestState()
    val StorageStatus(`testStorage`, _, ActorState.Active(_), storageRegions) = storages(testStorage)
    storageRegions shouldBe Set(testRegion)
    regions.keySet shouldBe Set(testRegion)
    regions(testRegion).storages shouldBe Set(testStorage)
  }

  it should "suspend region" in {
    supervisor ! SuspendRegion(testRegion)
    val (regions, storages) = requestState()
    regions(testRegion) shouldBe RegionStatus(testRegion, CoreTestUtils.regionConfig("testRegion"), ActorState.Suspended, Set(testStorage))
    storages.keySet shouldBe Set(testStorage)
    storages(testStorage).regions shouldBe Set(testRegion)
  }

  it should "resume region" in {
    supervisor ! ResumeRegion(testRegion)
    val (regions, storages) = requestState()
    val RegionStatus(`testRegion`, _, ActorState.Active(_), regionStorages) = regions(testRegion)
    regionStorages shouldBe Set(testStorage)
    storages.keySet shouldBe Set(testStorage)
    storages(testStorage).regions shouldBe Set(testRegion)
  }

  it should "delete region" in {
    supervisor ! DeleteRegion(testRegion)
    val (regions, storages) = requestState()
    regions shouldBe empty
    storages.keySet shouldBe Set(testStorage)
    storages(testStorage).regions shouldBe empty
  }

  private[this] def requestState() = {
    supervisor ! GetSnapshot
    val GetSnapshot.Success(_, RegionTracker.Snapshot(regions, storages)) = receiveOne(1 second)
    (regions, storages)
  }
}

