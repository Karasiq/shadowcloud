package com.karasiq.shadowcloud.metadata.markdown.test

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.metadata.{Metadata, MetadataParser}
import com.karasiq.shadowcloud.metadata.markdown.{FlexmarkMetadataParser, PlaintextMetadataParser}
import com.karasiq.shadowcloud.test.utils.{ActorSpec, ActorSpecImplicits}

class MarkdownMetadataProviderTest extends ActorSpec with ActorSpecImplicits with FlatSpecLike {
  val rootConfig = system.settings.config.getConfig("shadowcloud.metadata.markdown")

  testParser(FlexmarkMetadataParser(rootConfig.getConfig("flexmark")))("text.md", "text/markdown", "> Markdown test") { (format, text) ⇒
    format shouldBe "text/html"
    text shouldBe "<blockquote>\n<p>Markdown test</p>\n</blockquote>\n"
  }

  testParser(PlaintextMetadataParser(rootConfig.getConfig("plaintext")))("text.txt", "text/plain", "Plain test") { (format, text) ⇒
    format shouldBe "text/plain"
    text shouldBe "Plain test"
  }

  private[this] def testParser(parser: MetadataParser)(name: String, mime: String, data: String)(doTest: (String, String) ⇒ Unit): Unit = {
    parser.canParse(name, mime) shouldBe true

    val result = Source
      .single(ByteString(data))
      .via(parser.parseMetadata(name, mime))
      .runWith(Sink.seq)
      .futureValue

    result.collect { case Metadata(_, Metadata.Value.Text(Metadata.Text(format, text))) ⇒ (format, text) }.foreach(doTest.tupled)
  }
}
