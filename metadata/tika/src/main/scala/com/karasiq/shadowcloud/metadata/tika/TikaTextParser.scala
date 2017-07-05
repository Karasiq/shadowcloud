package com.karasiq.shadowcloud.metadata.tika

import java.io.InputStream

import com.typesafe.config.Config
import org.apache.tika.metadata.{Metadata ⇒ TikaMetadata}
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.sax.BodyContentHandler

import com.karasiq.shadowcloud.metadata.Metadata

private[tika] object TikaTextParser {
  def apply(config: Config): TikaTextParser = {
    new TikaTextParser(config)
  }
}

/**
  * Parses file to plain text
  * @param config Parser config
  * @see [[org.apache.tika.sax.BodyContentHandler#BodyContentHandler(int) BodyContentHandler]]
  */
private[tika] final class TikaTextParser(val config: Config) extends TikaMetadataParser {
  private[this] val enableFb2Fix = config.getBoolean("fb2-fix")
  override val parser = new AutoDetectParser

  override def canParse(name: String, mime: String): Boolean = {
    super.canParse(name, mime) || (enableFb2Fix && name.endsWith(".fb2.zip"))
  }

  protected def parseStream(metadata: TikaMetadata, inputStream: InputStream): Seq[Metadata] = {
    val handler = new BodyContentHandler(-1)
    parser.parse(inputStream, handler, metadata)
    val result = handler.toString
    if (result.nonEmpty) {
      Vector(Metadata(Some(Metadata.Tag("text", "tika")), Metadata.Value.Text(Metadata.Text("plain", result))))
    } else {
      Vector.empty
    }
  }
}
