package com.karasiq.shadowcloud.metadata.tika

import scala.collection.JavaConverters._

import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.IOUtils
import org.scalatest.{FlatSpec, Matchers}

class TikaMetadataProviderTest extends FlatSpec with Matchers {
  val testPdfFile = ByteString(IOUtils.toByteArray(getClass.getClassLoader.getResourceAsStream("TypeClasses.pdf")))

  val detector = TikaMimeDetector()
  "Mime detector" should "detect PDF" in {
    detector.getMimeType("TypeClasses.pdf", testPdfFile) shouldBe Some("application/pdf")
  }

  val textParserConfig = ConfigFactory.parseMap(Map(
    "fb2-fix" → true,
    "handle-all-mimes" → false,
    "extensions" → Seq("pdf").asJava,
    "mimes" → Seq("application/pdf").asJava
  ).asJava)
  val textParser = TikaTextParser(textParserConfig)

  "Text parser" should "extract text" in {
    val text = textParser.parseMetadata("TypeClasses.pdf", "application/pdf", testPdfFile)
      .flatMap(_.value.text)
      .head.data
    text.contains("Type Classes as Objects and Implicits") shouldBe true
  }
}
