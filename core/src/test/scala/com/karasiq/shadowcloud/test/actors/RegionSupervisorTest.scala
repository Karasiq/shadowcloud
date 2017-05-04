package com.karasiq.shadowcloud.test.actors

import scala.concurrent.duration._
import scala.language.postfixOps

import org.scalatest.{FlatSpecLike, Matchers}

import com.karasiq.shadowcloud.actors.RegionSupervisor
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}

class RegionSupervisorTest extends ActorSpec with FlatSpecLike with Matchers {
  import RegionSupervisor._
  val testRegion = "testRegion"
  val testStorage = "testStorage"
  val supervisor = system.actorOf(props, "supervisor")

  "Region supervisor" should "add region" in {
    supervisor ! AddRegion(testRegion, TestUtils.regionConfig("testRegion"))
    val (regions, storages) = requestState
    regions.keySet shouldBe Set(testRegion)
    val region = regions(testRegion)
    region.storages shouldBe empty
    region.regionId shouldBe testRegion
    storages shouldBe empty
  }

  it should "add storage" in {
    supervisor ! AddStorage(testStorage, StorageProps.inMemory)
    val (regions, storages) = requestState
    regions.keySet shouldBe Set(testRegion)
    storages.keySet shouldBe Set(testStorage)
    val storage = storages(testStorage)
    storage.regions shouldBe empty
    storage.props shouldBe StorageProps.inMemory
    storage.storageId shouldBe testStorage
  }

  it should "register storage" in {
    supervisor ! RegisterStorage(testRegion, testStorage)
    val (regions, storages) = requestState
    regions.keySet shouldBe Set(testRegion)
    storages.keySet shouldBe Set(testStorage)
    regions(testRegion).storages shouldBe Set(testStorage)
    storages(testStorage).regions shouldBe Set(testRegion)
  }

  it should "unregister storage" in {
    supervisor ! UnregisterStorage(testRegion, testStorage)
    val (regions, storages) = requestState
    regions.keySet shouldBe Set(testRegion)
    storages.keySet shouldBe Set(testStorage)
    regions(testRegion).storages shouldBe empty
    storages(testStorage).regions shouldBe empty
  }

  it should "delete storage" in {
    supervisor ! RegisterStorage(testRegion, testStorage)
    supervisor ! DeleteStorage(testStorage)
    val (regions, storages) = requestState
    regions.keySet shouldBe Set(testRegion)
    storages shouldBe empty
    regions(testRegion).storages shouldBe empty
    expectNoMsg(500 millis) // Wait for storage actor stop
  }

  it should "delete region" in {
    supervisor ! AddStorage(testStorage, StorageProps.inMemory)
    supervisor ! RegisterStorage(testRegion, testStorage)
    supervisor ! DeleteRegion(testRegion)
    val (regions, storages) = requestState
    regions shouldBe empty
    storages.keySet shouldBe Set(testStorage)
    storages(testStorage).regions shouldBe empty
  }

  private[this] def requestState = {
    supervisor ! GetState
    val GetState.Success(regions, storages) = receiveOne(1 second)
    (regions, storages)
  }
}

