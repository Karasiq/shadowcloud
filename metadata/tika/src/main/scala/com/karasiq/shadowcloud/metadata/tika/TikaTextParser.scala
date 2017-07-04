package com.karasiq.shadowcloud.metadata.tika

import java.io.ByteArrayInputStream

import akka.util.ByteString
import org.apache.tika.metadata.{Metadata ⇒ TikaMetadata}
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler

import com.karasiq.shadowcloud.metadata.{Metadata, MetadataParser}

private[tika] object TikaTextParser {
  def apply(): TikaTextParser = {
    new TikaTextParser()
  }
}

private[tika] final class TikaTextParser extends MetadataParser {
  def canParse(name: String, mime: String): Boolean = {
    true // TODO: Filter supported formats
  }

  def parseMetadata(name: String, mime: String, data: ByteString): Seq[Metadata] = {
    val handler = new BodyContentHandler
    val parser = new AutoDetectParser
    val metadata = new TikaMetadata
    val stream = new ByteArrayInputStream(data.toArray)
    parser.parse(stream, handler, metadata)

    val result = parser.toString
    if (result.nonEmpty) {
      Vector(Metadata(Some(Metadata.Tag("text", "tika")), Metadata.Value.Text(Metadata.Text("plain", result))))
    } else {
      Vector.empty
    }
  }
}
