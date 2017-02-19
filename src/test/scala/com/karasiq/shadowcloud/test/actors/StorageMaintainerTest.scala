package com.karasiq.shadowcloud.test.actors

import akka.testkit.TestActorRef
import akka.util.ByteString
import com.karasiq.shadowcloud.actors.StorageMaintainer
import com.karasiq.shadowcloud.actors.events.StorageEvent
import com.karasiq.shadowcloud.actors.events.StorageEvent.StorageEnvelope
import com.karasiq.shadowcloud.storage.StorageHealthProvider
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}
import org.scalatest.FlatSpecLike

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.language.postfixOps

class StorageMaintainerTest extends ActorSpec with FlatSpecLike {
  val chunkMap = TrieMap.empty[String, ByteString]
  val healthProvider = StorageHealthProvider.fromMaps(chunkMap)
  val initialHealth = healthProvider.health.futureValue
  val storageMaintainer = TestActorRef(StorageMaintainer.props("testStorage", testActor, healthProvider))

  "Storage maintainer" should "check storage health" in {
    chunkMap += "1" → TestUtils.randomBytes(100)
    storageMaintainer ! StorageMaintainer.CheckHealth // Force
    val StorageEnvelope("testStorage", StorageEvent.HealthUpdated(health)) = receiveOne(1 second)
    health.totalSpace shouldBe initialHealth.totalSpace
    health.usedSpace shouldBe (initialHealth.usedSpace + 100)
    health.canWrite shouldBe (initialHealth.canWrite - 100)
  }

  it should "react to chunk write" in {
    val chunk = TestUtils.randomChunk
    storageMaintainer ! StorageEnvelope("testStorage", StorageEvent.ChunkWritten(chunk))
    val StorageEnvelope("testStorage", StorageEvent.HealthUpdated(health)) = receiveOne(1 second)
    health.totalSpace shouldBe initialHealth.totalSpace
    health.usedSpace shouldBe (initialHealth.usedSpace + 100 + chunk.checksum.size)
    health.canWrite shouldBe (initialHealth.canWrite - 100 - chunk.checksum.size)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    StorageEvent.stream.subscribe(testActor, "testStorage")
  }

  override protected def afterAll(): Unit = {
    StorageEvent.stream.unsubscribe(testActor)
    super.afterAll()
  }
}
