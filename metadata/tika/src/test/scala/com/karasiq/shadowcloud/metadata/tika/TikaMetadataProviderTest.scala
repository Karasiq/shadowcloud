package com.karasiq.shadowcloud.metadata.tika

import java.io.File

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Keep}
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.apache.tika.Tika
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import com.karasiq.shadowcloud.metadata.Metadata

class TikaMetadataProviderTest extends TestKit(ActorSystem("test")) with FlatSpecLike with Matchers with BeforeAndAfterAll {
  implicit val materializer = ActorMaterializer()(system)
  val testPdfFile = new File(getClass.getClassLoader.getResource("TypeClasses.pdf").toURI)
  val testPdfBytes = ByteString(FileUtils.readFileToByteArray(testPdfFile))

  val tika = new Tika()
  val detector = TikaMimeDetector(tika)
  "Mime detector" should "detect PDF" in {
    detector.getMimeType("TypeClasses.pdf", testPdfBytes) shouldBe Some("application/pdf")
  }

  val autoParserConfig = ConfigFactory.load().getConfig("shadowcloud.metadata.tika.auto-parser")
  val autoParser = TikaAutoParser(tika, autoParserConfig)

  "Parser" should "extract text" in {
    val output = FileIO.fromPath(testPdfFile.toPath)
      .via(autoParser.parseMetadata("TypeClasses.pdf", "application/pdf"))
      .toMat(TestSink.probe)(Keep.right)
      .run()

    val text = output.requestNext(10 seconds)
    text.tag shouldBe Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.CONTENT))
    text.value.text.exists(t ⇒ t.format == "text/plain" && t.data.contains("Type Classes as Objects and Implicits")) shouldBe true

    val xml = output.requestNext()
    xml.tag shouldBe Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.CONTENT))
    xml.value.text.exists(t ⇒ t.format == "text/html" && t.data.contains("<p>Adriaan Moors Martin Odersky\nEPFL\n</p>")) shouldBe true

    val metaTable = output.requestNext()
    metaTable.tag shouldBe Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.METADATA))
    metaTable.value.table.exists(_.values("created").values == Seq("Mon Jul 26 13:01:12 MSD 2010")) shouldBe true

    output.request(1)
    output.expectComplete()
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }
}
