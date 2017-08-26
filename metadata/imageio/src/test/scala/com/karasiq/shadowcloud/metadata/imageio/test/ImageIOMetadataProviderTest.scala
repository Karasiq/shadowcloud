package com.karasiq.shadowcloud.metadata.imageio.test

import java.awt.image.BufferedImage

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.scalatest.concurrent.ScalaFutures

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.metadata.imageio.ImageIOThumbnailCreator
import com.karasiq.shadowcloud.metadata.imageio.utils.ImageIOResizer

class ImageIOMetadataProviderTest extends TestKit(ActorSystem("imageio-test"))
  with FlatSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures {

  implicit val materializer = ActorMaterializer()
  val thumbnailCreator = ImageIOThumbnailCreator(ConfigFactory.load().getConfig("shadowcloud.metadata.imageio.thumbnails"))

  "Thumbnail creator" should "create a thumbnail" in {
    testThumbnail("thumb-test/test.jpg", "jpeg", 755, 576, 200, 152)
    testThumbnail("thumb-test/test.png", "png", 755, 576, 200, 152)
    testThumbnail("thumb-test/test.gif", "gif", 755, 576, 200, 152)
  }

  private[this] def testThumbnail(name: String, imageType: String, width: Int, height: Int, tWidth: Int, tHeight: Int): Unit = {
    /* val (width, height) = {
      val resStream = createResourceStream(name)
        .fold(ByteString.empty)(_ ++ _)
        .map(bs ⇒ ImageIOResizer.loadImage(bs.toArray))
        .runWith(Sink.head)
      val image = resStream.futureValue
      (image.getWidth, image.getHeight)
    } */

    val testStream = createResourceStream(name)
      .via(thumbnailCreator.parseMetadata(name, s"image/$imageType"))
      .runWith(TestSink.probe)

    val imageData = testStream.requestNext(5 seconds)
    val imageDataValue = imageData.value.imageData.get
    imageDataValue.width shouldBe width
    imageDataValue.height shouldBe height

    val thumb = testStream.requestNext(5 seconds)
    val thumbImage = extractThumbnail(thumb)
    thumbImage.getWidth shouldBe tWidth
    thumbImage.getHeight shouldBe tHeight

    testStream.request(1)
    testStream.expectComplete()
  }

  private[this] def extractThumbnail(md: Metadata): BufferedImage = {
    ImageIOResizer.loadImage(md.getThumbnail.data.toArray)
  }

  private[this] def createResourceStream(name: String): Source[ByteString, NotUsed] = {
    StreamConverters.fromInputStream(() ⇒ getClass.getClassLoader.getResourceAsStream(name))
      .mapMaterializedValue(_ ⇒ NotUsed)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}
