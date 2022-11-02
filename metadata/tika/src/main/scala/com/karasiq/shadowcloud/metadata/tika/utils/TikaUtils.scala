package com.karasiq.shadowcloud.metadata.tika.utils

import org.apache.tika.sax.{BodyContentHandler, TeeContentHandler, ToXMLContentHandler}
import org.jsoup.Jsoup
import org.xml.sax.ContentHandler

import com.karasiq.shadowcloud.metadata.Metadata

private[tika] object TikaUtils {
  val PluginId = "tika"

  def getHtmlContent(xhtml: String): String = {
    val document     = Jsoup.parse(xhtml)
    val documentBody = document.body()
    documentBody.html()
  }

  def getHtmlMetaValue(xhtml: String, name: String): Option[String] = {
    import scala.collection.JavaConverters._
    val document = Jsoup.parse(xhtml)
    val element = document
      .head()
      .children()
      .asScala
      .find(e ⇒ e.tagName() == "meta" && e.attr("name") == name)
    element.map(_.attr("content")).filter(_.nonEmpty)
  }

  def extractMetadataPath(md: Metadata): Option[String] = {
    val pathString = md match {
      case m if m.value.isTable ⇒
        m.getTable.values.get(TikaAttributes.ResourceName).flatMap(_.values.headOption)

      case m if m.value.isText && m.getText.format == TikaFormats.HTML ⇒
        getHtmlMetaValue(m.getText.data, TikaAttributes.ResourceName)

      case _ ⇒
        None
    }

    pathString.filter(_.nonEmpty)
  }

  def hasContent(text: String, format: String): Boolean = {
    if (format == TikaFormats.HTML) getHtmlContent(text).nonEmpty else text.trim.nonEmpty
  }

  def parseTimeString(timeString: String): Long = {
    import java.time.ZonedDateTime
    ZonedDateTime.parse(timeString).toInstant.toEpochMilli
  }

  object ContentWrapper {
    def apply(parserId: String, textLimit: Int, xhtmlEnabled: Boolean) = {
      new TikaUtils.ContentWrapper(
        TikaUtils.PluginId,
        parserId,
        Some(new BodyContentHandler(textLimit)).filter(_ ⇒ textLimit > 0),
        Some(new ToXMLContentHandler()).filter(_ ⇒ xhtmlEnabled)
      )
    }
  }

  final class ContentWrapper(plugin: String, parser: String, textHandler: Option[ContentHandler], xhtmlHandler: Option[ContentHandler]) {
    private[this] final class TeeContentHandlerWrapper(subHandlers: Seq[ContentHandler]) extends TeeContentHandler(subHandlers: _*) {
      override def toString: String = "" // RecursiveParserWrapper fix
    }

    def toContentHandler: ContentHandler = {
      val subHandlers = Seq(textHandler, xhtmlHandler).flatten
      new TeeContentHandlerWrapper(subHandlers)
    }

    def extractContents(): Seq[Metadata] = {
      def extractText(handler: Option[ContentHandler], format: String): Option[Metadata] = {
        handler
          .map(_.toString)
          .filter(TikaUtils.hasContent(_, format))
          .map(result ⇒
            Metadata(Some(Metadata.Tag(plugin, parser, Metadata.Tag.Disposition.CONTENT)), Metadata.Value.Text(Metadata.Text(format, result)))
          )
      }

      Seq(
        extractText(textHandler, TikaFormats.Text),
        extractText(xhtmlHandler, TikaFormats.HTML)
      ).flatten
    }
  }
}
