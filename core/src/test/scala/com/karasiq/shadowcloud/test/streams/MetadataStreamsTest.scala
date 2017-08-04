package com.karasiq.shadowcloud.test.streams

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.index.{File, Path}
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.streams.metadata.MimeDetectorStream
import com.karasiq.shadowcloud.test.utils.{ActorSpec, TestUtils}
import com.karasiq.shadowcloud.utils.{HexString, Utils}

class MetadataStreamsTest extends ActorSpec with FlatSpecLike {
  val testJpegFileStream = TestUtils.getResourceStream("14935431092820.jpg")

  "Mime detector" should "detect mime type" in {
    val testOut = testJpegFileStream
      .via(MimeDetectorStream(sc.modules.metadata, "14935431092820.jpg", 10000))
      .runWith(TestSink.probe)
    
    val mimeType = testOut.requestNext()
    mimeType shouldBe "image/jpeg"

    testOut.request(1)
    testOut.expectComplete()
  }

  val testRegionId = "metadataStreamsTest"
  val testStorageId = "metadataStreamsTest"
  val testFileId = File.newFileId
  val testMetadata = Metadata(Some(Metadata.Tag("test", "test", Metadata.Tag.Disposition.PREVIEW)),
    Metadata.Value.Preview(Metadata.Preview("random", TestUtils.randomBytes(100))))

  "Metadata streams" should "write metadata" in {
    registerRegionAndStorages()

    val (testIn, testOut) = TestSource.probe[Metadata]
      .via(sc.streams.metadata.writeAll(testRegionId, testFileId))
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

  it should "create metadata" in {
    val testFileStream = testJpegFileStream
      .via(sc.streams.metadata.create("test.jpg"))
      .runWith(TestSink.probe)

    testFileStream.requestNext().tag shouldBe Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.CONTENT))
    testFileStream.requestNext().tag shouldBe Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.METADATA))
    testFileStream.requestNext().tag shouldBe Some(Metadata.Tag("imageio", "thumbnail", Metadata.Tag.Disposition.METADATA))
    testFileStream.requestNext().tag shouldBe Some(Metadata.Tag("imageio", "thumbnail", Metadata.Tag.Disposition.PREVIEW))

    testFileStream.request(1)
    testFileStream.expectComplete()
  }

  it should "write metadata on the fly" in {
    val testFileStream = testJpegFileStream
      .via(sc.streams.metadata.writeFileAndMetadata(testRegionId, Path.root / "test.jpg"))
      .runWith(TestSink.probe)

    val (testFileResult, writtenMetadataFiles) = testFileStream.requestNext()
    testFileStream.request(1)
    testFileStream.expectComplete()

    testFileResult.path shouldBe Path.root / "test.jpg"
    testFileResult.revision shouldBe 0
    testFileResult.chunks.map(c ⇒ HexString.encode(c.checksum.hash)) shouldBe Seq("f7c59bab99a349128fffea1e62002aac4be468d80eec1744ad1cb0363653bd05")

    val resultMetadata = sc.streams.metadata.list(testRegionId, testFileResult.id).futureValue
    resultMetadata.folders shouldBe empty
    resultMetadata.files shouldBe writtenMetadataFiles.toSet
    resultMetadata.files.map(_.path.name) shouldBe Set("content", "preview", "metadata")
    println(resultMetadata)
  }

  private[this] def registerRegionAndStorages(): Unit = {
    sc.ops.supervisor.addRegion(testRegionId, sc.configs.regionConfig(testRegionId))
    sc.ops.supervisor.addStorage(testStorageId, StorageProps.inMemory)
    sc.ops.supervisor.register(testRegionId, testStorageId)
    expectNoMsg(100 millis)
  }
}
