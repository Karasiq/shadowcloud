package com.karasiq.shadowcloud.metadata.tika

import java.io.{InputStream, OutputStream}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

import com.typesafe.config.Config
import org.apache.tika.Tika
import org.apache.tika.metadata.{Metadata ⇒ TikaMetadata}
import org.apache.tika.parser.{ParseContext, RecursiveParserWrapper}
import org.apache.tika.sax._
import org.jsoup.Jsoup
import org.xml.sax.ContentHandler

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.metadata.Metadata.Tag.{Disposition ⇒ MDDisposition}
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.utils.Utils

private[tika] object TikaAutoParser {
  def apply(tika: Tika, config: Config): TikaAutoParser = {
    new TikaAutoParser(tika, config)
  }

  private object attributes {
    val path = "resourceName"
    val size = "Content-Length"
    val lastModified = "Last-Modified"

    def optional(md: TikaMetadata, name: String): Option[String] = {
      Option(md.get(name)).filter(_.nonEmpty)
    }
  }

  private object formats {
    val html = "text/html"
    val text = "text/plain"
  }

  private object conversions {
    def toMetadataTable(metadata: TikaMetadata): Option[Metadata] = {
      Option(metadata)
        .filter(_.size() > 0)
        .map { metadataMap ⇒
          val values = metadataMap.names()
            .map(name ⇒ (name, Metadata.Table.Values(metadataMap.getValues(name).toVector)))
            .toMap
          Metadata(TikaAutoParser.createTag(MDDisposition.METADATA), Metadata.Value.Table(Metadata.Table(values)))
        }
    }

    def toEmbeddedResources(metadatas: Seq[Seq[Metadata]]): Option[Metadata] = {
      val resources = metadatas.map { metadatas ⇒
        val resourcePaths = metadatas.flatMap(utils.extractMetadataPath)
        Metadata.EmbeddedResources.EmbeddedResource(resourcePaths.headOption.getOrElse(""), metadatas)
      }

      val groupedResources = resources.filter(_.metadata.nonEmpty)
        .groupBy(_.path)
        .map { case (path, resources) ⇒
          val allMetadatas = resources.flatMap(_.metadata)
          Metadata.EmbeddedResources.EmbeddedResource(path, allMetadatas)
        }

      Some(groupedResources)
        .filter(_.nonEmpty)
        .map(resources ⇒ Metadata(TikaAutoParser.createTag(MDDisposition.CONTENT),
          Metadata.Value.EmbeddedResources(Metadata.EmbeddedResources(resources.toVector))))
    }

    def toArchiveTable(subMetadatas: Seq[TikaMetadata]): Option[Metadata] = {
      val archiveFiles = for {
        md ← subMetadatas
        path ← attributes.optional(md, attributes.path).map(ps ⇒ Path.fromString(ps).nodes)
        size ← attributes.optional(md, attributes.size).map(_.toLong)
        timestamp = attributes.optional(md, attributes.lastModified).fold(0L)(utils.parseTimeString)
      } yield Metadata.FileList.File(path, size, timestamp)

      Some(archiveFiles)
        .filter(_.nonEmpty)
        .map(files ⇒ Metadata(TikaAutoParser.createTag(MDDisposition.PREVIEW),
          Metadata.Value.FileList(Metadata.FileList(files))))
    }

    def toTextPreviews(metadatas: Seq[Metadata], maxLength: Int): Seq[Metadata] = {
      metadatas.flatMap(_.value.text)
        .filter(_.format == formats.text)
        .map(text ⇒ Utils.takeWords(text.data, maxLength))
        .filter(_.nonEmpty)
        .sortBy(_.length)(Ordering[Int].reverse)
        .map(result ⇒ Metadata(TikaAutoParser.createTag(MDDisposition.PREVIEW),
          Metadata.Value.Text(Metadata.Text(formats.text, result))))
    }
  }

  private object utils {
    def getHtmlContent(xhtml: String): String = {
      val document = Jsoup.parse(xhtml)
      val documentBody = document.body()
      documentBody.html()
    }

    def getHtmlMetaValue(xhtml: String, name: String): Option[String] = {
      import scala.collection.JavaConverters._
      val document = Jsoup.parse(xhtml)
      val element = document.head().children().asScala
        .find(e ⇒ e.tagName() == "meta" && e.attr("name") == name)
      element.map(_.attr("content")).filter(_.nonEmpty)
    }

    def extractMetadataPath(md: Metadata): Option[String] = {
      val pathString = md match {
        case m if m.value.isTable ⇒
          m.getTable.values.get(attributes.path).flatMap(_.values.headOption)

        case m if m.value.isText && m.getText.format == formats.html ⇒
          getHtmlMetaValue(m.getText.data, attributes.path)

        case _ ⇒
          None
      }

      pathString.filter(_.nonEmpty)
    }

    def hasContent(text: String, format: String): Boolean = {
      if (format == formats.html) getHtmlContent(text).nonEmpty else text.trim.nonEmpty
    }

    def parseTimeString(timeString: String): Long = {
      import java.time.ZonedDateTime
      ZonedDateTime.parse(timeString)
        .toInstant
        .toEpochMilli
    }

    final class ContentHandlersWrapper(textHandler: Option[ContentHandler], xhtmlHandler: Option[ContentHandler]) {
      private[this] final class TeeContentHandlerWrapper(subHandlers: Seq[ContentHandler]) extends TeeContentHandler(subHandlers:_*) {
        override def toString: String = "" // RecursiveParserWrapper fix
      }

      def createHandler(): ContentHandler = {
        val subHandlers = Seq(textHandler, xhtmlHandler).flatten
        new TeeContentHandlerWrapper(subHandlers)
      }

      def extractContents(): Seq[Metadata] = {
        def extractText(handler: Option[ContentHandler], format: String): Option[Metadata] = {
          handler
            .map(_.toString)
            .filter(utils.hasContent(_, format))
            .map(result ⇒ Metadata(TikaAutoParser.createTag(MDDisposition.CONTENT),
              Metadata.Value.Text(Metadata.Text(format, result))))
        }

        Seq(
          extractText(textHandler, formats.text),
          extractText(xhtmlHandler, formats.html)
        ).flatten
      }
    }
  }

  private def createTag(disposition: Metadata.Tag.Disposition): Option[Metadata.Tag] = {
    Some(Metadata.Tag("tika", "auto", disposition))
  }
}

/**
  * Parses file with default Tika parser
  * @param config Parser config
  */
private[tika] final class TikaAutoParser(tika: Tika, val config: Config) extends TikaMetadataParser {
  import TikaAutoParser.{conversions, utils}

  // Configuration
  private[this] object autoParserSettings extends ConfigImplicits {
    // Parser
    val recursive = config.getBoolean("recursive")
    val fb2Fix = config.getBoolean("fb2-fix")

    // Text handler
    val textEnabled = config.getBoolean("text.enabled")
    val textLimit = config.getBytesInt("text.limit")
    val textPreviewSize = config.getBytesInt("text.preview-size")

    // XHTML handler
    val xhtmlEnabled = config.getBoolean("xhtml.enabled")
  }

  override val parser = tika.getParser

  override def canParse(name: String, mime: String): Boolean = {
    super.canParse(name, mime) || (autoParserSettings.fb2Fix && name.endsWith(".fb2.zip"))
  }

  protected def parseStream(metadata: TikaMetadata, inputStream: InputStream): Seq[Metadata] = {
    val (rawMetadatas, contentHandlers) = parseMetadataTablesAndContents(metadata, inputStream, autoParserSettings.recursive)

    // Wrap results
    val mainContents = contentHandlers.headOption.toVector.flatMap(_.extractContents())
    val mainMetadata = rawMetadatas.headOption.flatMap(conversions.toMetadataTable)

    val subRawMetadatas = rawMetadatas.drop(1)
    val subContents = contentHandlers.drop(1)
    val subMetadatas = subRawMetadatas.map(conversions.toMetadataTable(_).toSeq) ++ subContents.map(_.extractContents())
    val mainMetadatas = mainContents ++ mainMetadata ++ conversions.toArchiveTable(subRawMetadatas)

    mainMetadatas ++
      conversions.toTextPreviews(mainMetadatas ++ subMetadatas.flatten, autoParserSettings.textPreviewSize) ++
      conversions.toEmbeddedResources(subMetadatas)
  }

  private[this] def parseMetadataTablesAndContents(metadata: TikaMetadata, inputStream: InputStream,
                                                   recursive: Boolean): (Vector[TikaMetadata], Vector[utils.ContentHandlersWrapper]) = {
    if (recursive) {
      // Recursive parser
      val handlers = new ArrayBuffer[utils.ContentHandlersWrapper]()
      val contentHandlerFactory = new ContentHandlerFactory {
        def getNewContentHandler: ContentHandler = {
          val newHandler = createContentHandlers()
          handlers += newHandler
          newHandler.createHandler()
        }

        def getNewContentHandler(os: OutputStream, encoding: String): ContentHandler = {
          getNewContentHandler
          // throw new IllegalArgumentException("Not supported")
        }
      }

      val parser = new RecursiveParserWrapper(this.parser, contentHandlerFactory)
      try {
        parser.parse(inputStream, null, metadata, new ParseContext)
      } catch { case NonFatal(_) ⇒
        // Ignore
      }

      (parser.getMetadata.asScala.toVector, handlers.toVector)
    } else {
      // Plain parser
      val handler = createContentHandlers()

      try {
        this.parser.parse(inputStream, handler.createHandler(), metadata, new ParseContext)
      } catch { case NonFatal(_) ⇒
        // Ignore
      }

      (Vector(metadata), Vector(handler))
    }
  }

  private[this] def createContentHandlers(): utils.ContentHandlersWrapper = {
    new utils.ContentHandlersWrapper(
      Some(new BodyContentHandler(autoParserSettings.textLimit)).filter(_ ⇒ autoParserSettings.textEnabled),
      Some(new ToXMLContentHandler()).filter(_ ⇒ autoParserSettings.xhtmlEnabled)
    )
  }
}
