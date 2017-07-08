package com.karasiq.shadowcloud.metadata.tika

import java.io.InputStream

import com.typesafe.config.Config
import org.apache.tika.Tika
import org.apache.tika.metadata.{Metadata ⇒ TikaMetadata}
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.{BodyContentHandler, TeeContentHandler, ToXMLContentHandler}
import org.xml.sax.SAXException

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.metadata.Metadata

private[tika] object TikaAutoParser {
  def apply(tika: Tika, config: Config): TikaAutoParser = {
    new TikaAutoParser(tika, config)
  }
}

/**
  * Parses file with default Tika parser
  * @param config Parser config
  */
private[tika] final class TikaAutoParser(tika: Tika, val config: Config) extends TikaMetadataParser with ConfigImplicits {
  private[this] val enableFb2Fix = config.getBoolean("fb2-fix")
  private[this] val textEnabled = config.getBoolean("text.enabled")
  private[this] val textLimit = config.getBytesInt("text.limit")
  private[this] val xhtmlEnabled = config.getBoolean("xhtml.enabled")

  override val parser = tika.getParser

  override def canParse(name: String, mime: String): Boolean = {
    super.canParse(name, mime) || (enableFb2Fix && name.endsWith(".fb2.zip"))
  }

  protected def parseStream(metadata: TikaMetadata, inputStream: InputStream): Seq[Metadata] = {
    val textHandler = new BodyContentHandler(textLimit)
    val xhtmlHandler = new ToXMLContentHandler()
    val handler = {
      val handlers = Seq(
        Some(textHandler).filter(_ ⇒ textEnabled),
        Some(xhtmlHandler).filter(_ ⇒ xhtmlEnabled)
      )
      new TeeContentHandler(handlers.flatten:_*)
    }

    try {
      parser.parse(inputStream, handler, metadata, new ParseContext)
    } catch { case _: SAXException ⇒
      // Ignore
    }

    def extractText(): Option[Metadata] = {
      Option(textHandler.toString)
        .filter(_.nonEmpty)
        .map(result ⇒ Metadata(Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.CONTENT)), Metadata.Value.Text(Metadata.Text("txt", result))))
    }

    def extractHtml(): Option[Metadata] = {
      Option(xhtmlHandler.toString)
        .filter(_.nonEmpty)
        .map(result ⇒ Metadata(Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.CONTENT)), Metadata.Value.Text(Metadata.Text("xhtml", result))))
    }

    def extractMetadataTable(): Option[Metadata] = {
      Some(metadata)
        .filter(_.size() > 0)
        .map { metadataMap ⇒
          val values = metadataMap.names()
            .map(name ⇒ (name, Metadata.Table.Values(metadataMap.getValues(name).toVector)))
            .toMap
          Metadata(Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.METADATA)), Metadata.Value.Table(Metadata.Table(values)))
        }
    }

    Vector(
      extractText(),
      extractHtml(),
      extractMetadataTable()
    ).flatten
  }
}
