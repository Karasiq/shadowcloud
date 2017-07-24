package com.karasiq.shadowcloud.test.streams

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.index.File
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}
import com.karasiq.shadowcloud.utils.Utils

class MetadataStreamsTest extends ActorSpec with FlatSpecLike {
  val testRegionId = "metadataStreamsTest"
  val testStorageId = "metadataStreamsTest"
  val testFileId = File.newFileId
  val testMetadata = Metadata(Some(Metadata.Tag("test", "test", Metadata.Tag.Disposition.PREVIEW)),
    Metadata.Value.Preview(Metadata.Preview("random", TestUtils.randomBytes(100))))

  "Metadata streams" should "write metadata" in {
    registerRegionAndStorages()

    val (testIn, testOut) = TestSource.probe[Metadata]
      .via(sc.streams.metadata.write(testRegionId, testFileId))
      .toMat(TestSink.probe)(Keep.both)
      .run()

    testIn.sendNext(testMetadata)
    testIn.sendComplete()

    testOut.request(2)
    val resultFile = testOut.expectNext()
    testOut.expectComplete()

    resultFile.checksum.size should be > 0L
    resultFile.path shouldBe (Utils.internalFolderPath / "metadata" / testFileId.toString.toLowerCase / "preview")
  }

  it should "read metadata" in {
    val testOut = sc.streams.metadata.read(testRegionId, testFileId, Metadata.Tag.Disposition.PREVIEW)
      .runWith(TestSink.probe)

    testOut.request(2)
    testOut.expectNext(testMetadata)
    testOut.expectComplete()
  }

  it should "delete metadata" in {
    sc.streams.metadata.delete(testRegionId, testFileId).futureValue.files should not be empty

    val testOut = sc.streams.metadata.read(testRegionId, testFileId, Metadata.Tag.Disposition.PREVIEW)
      .runWith(TestSink.probe)

    testOut.request(1)
    testOut.expectComplete()
  }

  private[this] def registerRegionAndStorages(): Unit = {
    sc.ops.supervisor.addRegion(testRegionId, sc.regionConfig(testRegionId))
    sc.ops.supervisor.addStorage(testStorageId, StorageProps.inMemory)
    sc.ops.supervisor.register(testRegionId, testStorageId)
    expectNoMsg(100 millis)
  }
}
