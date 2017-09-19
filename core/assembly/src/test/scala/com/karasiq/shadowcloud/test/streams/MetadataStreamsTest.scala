package com.karasiq.shadowcloud.test.streams

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import org.scalatest.FlatSpecLike
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model.{FileId, Path}
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.streams.metadata.MimeDetectorStream
import com.karasiq.shadowcloud.test.utils.{ResourceUtils, SCExtensionSpec, TestUtils}
import com.karasiq.shadowcloud.utils.Utils
import com.karasiq.shadowcloud.utils.encoding.HexString

object MetadataStreamsTest {
  def testJpegStream() = ResourceUtils.toStream("14935431092820.jpg")
}

class MetadataStreamsTest extends SCExtensionSpec with FlatSpecLike {
  "Mime detector" should "detect mime type" in {
    val testOut = MetadataStreamsTest.testJpegStream()
      .via(MimeDetectorStream(sc.modules.metadata, "14935431092820.jpg", 10000))
      .runWith(TestSink.probe)
    
    val mimeType = testOut.requestNext()
    mimeType shouldBe "image/jpeg"

    testOut.request(1)
    testOut.expectComplete()
  }

  val testRegionId = "metadataStreamsTest"
  val testStorageId = "metadataStreamsTest"
  val testFileId = FileId.create()
  val testMetadata = Metadata(Some(Metadata.Tag("test", "test", Metadata.Tag.Disposition.PREVIEW)),
    Metadata.Value.Thumbnail(Metadata.Thumbnail("random", TestUtils.randomBytes(100))))

  "Metadata streams" should "write metadata" in {
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
    resultFile.path shouldBe (Utils.InternalFolder / "metadata" / testFileId.toString.toLowerCase / "preview")
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
    val testTags = MetadataStreamsTest.testJpegStream()
      .via(sc.streams.metadata.create("test.jpg"))
      .runWith(Sink.seq)
      .futureValue(Timeout(10 seconds))

    val expectedTags = Set(
      Metadata.Tag("imageio", "thumbnail", Metadata.Tag.Disposition.METADATA),
      Metadata.Tag("imageio", "thumbnail", Metadata.Tag.Disposition.PREVIEW),
      Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.METADATA)
    )

    testTags.flatMap(_.tag).toSet shouldBe expectedTags
  }

  it should "write metadata on the fly" in {
    val testFileStream = MetadataStreamsTest.testJpegStream()
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
    resultMetadata.files.map(_.path.name) shouldBe Set("preview", "metadata")
    println(resultMetadata)
  }

  private[this] def registerRegionAndStorages(): Unit = {
    sc.ops.supervisor.createRegion(testRegionId, sc.configs.regionConfig(testRegionId))
    sc.ops.supervisor.createStorage(testStorageId, StorageProps.inMemory)
    sc.ops.supervisor.register(testRegionId, testStorageId)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    registerRegionAndStorages()
  }
}
