package com.karasiq.shadowcloud.metadata.tika

import java.io.{InputStream, OutputStream}
import java.time.ZonedDateTime

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

import com.typesafe.config.Config
import org.apache.tika.Tika
import org.apache.tika.metadata.{Metadata ⇒ TikaMetadata}
import org.apache.tika.parser.{ParseContext, RecursiveParserWrapper}
import org.apache.tika.sax._
import org.xml.sax.ContentHandler

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.index.Path
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
private[tika] final class TikaAutoParser(tika: Tika, val config: Config) extends TikaMetadataParser {
  // Configuration
  private[this] object autoParserSettings extends ConfigImplicits {
    // Parser
    val recursive = config.getBoolean("recursive")
    val fb2Fix = config.getBoolean("fb2-fix")

    // Text handler
    val textEnabled = config.getBoolean("text.enabled")
    val textLimit = config.getBytesInt("text.limit")

    // XHTML handler
    val xhtmlEnabled = config.getBoolean("xhtml.enabled")
  }

  override val parser = tika.getParser

  override def canParse(name: String, mime: String): Boolean = {
    super.canParse(name, mime) || (autoParserSettings.fb2Fix && name.endsWith(".fb2.zip"))
  }

  protected def parseStream(metadata: TikaMetadata, inputStream: InputStream): Seq[Metadata] = {
    def wrapMetadataTable(metadata: TikaMetadata): Option[Metadata] = {
      Option(metadata)
        .filter(_.size() > 0)
        .map { metadataMap ⇒
          val values = metadataMap.names()
            .map(name ⇒ (name, Metadata.Table.Values(metadataMap.getValues(name).toVector)))
            .toMap
          Metadata(Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.METADATA)), Metadata.Value.Table(Metadata.Table(values)))
        }
    }

    def wrapSubMetadatas(metadatas: Seq[Seq[Metadata]]): Option[Metadata] = {
      val resources = metadatas.map { metadatas ⇒
        val resourcePath = metadatas.collectFirst {
          case m if m.value.isTable && m.getTable.values.contains("resourceName") ⇒
            m.getTable.values("resourceName").values.head
        }
        Metadata.EmbeddedResources.EmbeddedResource(resourcePath.getOrElse(""), metadatas)
      }

      Some(resources.filter(_.metadata.nonEmpty))
        .filter(_.nonEmpty)
        .map(resources ⇒ Metadata(Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.CONTENT)),
          Metadata.Value.EmbeddedResources(Metadata.EmbeddedResources(resources))))
    }

    def extractArchiveTable(subMetadatas: Seq[TikaMetadata]): Option[Metadata] = {
      def toTimestamp(timeString: String): Long = {
        ZonedDateTime.parse(timeString)
          .toInstant
          .toEpochMilli
      }

      val archiveFiles = for {
        md ← subMetadatas
        path ← Option(md.get("resourceName")).map(Path.fromString)
        size ← Option(md.get("Content-Length")).map(_.toLong)
        timestamp = Option(md.get("Last-Modified")).fold(0L)(toTimestamp)
      } yield Metadata.ArchiveFiles.ArchiveFile(path.nodes, size, timestamp)

      Some(archiveFiles)
        .filter(_.nonEmpty)
        .map(files ⇒ Metadata(Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.PREVIEW)),
          Metadata.Value.ArchiveFiles(Metadata.ArchiveFiles(files))))
    }

    def extractTextPreview(metadatas: Seq[Metadata]): Seq[Metadata] = {
      def takeWords(str: String, maxLength: Int): String = {
        def cutAt(separator: String): Option[String] = {
          val index = str.lastIndexOf(separator, maxLength)
          if (index == -1) None
          else Some(str.substring(0, index + 1))
        }

        cutAt(". ")
          .orElse(cutAt("\n"))
          .orElse(cutAt(" "))
          .getOrElse(str.take(maxLength))
      }

      metadatas.flatMap(_.value.text)
        .filter(_.format == "text/plain")
        .map(text ⇒ takeWords(text.data, 1000))
        .filter(_.nonEmpty)
        .sortBy(_.length)(Ordering[Int].reverse)
        .map(result ⇒ Metadata(Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.PREVIEW)), Metadata.Value.Text(Metadata.Text("text/plain", result))))
    }

    val (rawMetadatas, contentHandlers) = if (autoParserSettings.recursive) {
      // Recursive parser
      val handlers = new ArrayBuffer[Handlers]()
      val recursiveParserWrapper = new RecursiveParserWrapper(this.parser, new ContentHandlerFactory {
        def getNewContentHandler: ContentHandler = {
          val newHandler = createContentHandlers()
          handlers += newHandler
          newHandler.createHandler()
        }

        def getNewContentHandler(os: OutputStream, encoding: String): ContentHandler = {
          throw new IllegalArgumentException("Not supported")
        }
      })
      try {
        recursiveParserWrapper.parse(inputStream, null, metadata, new ParseContext)
      } catch { case NonFatal(_) ⇒
        // Ignore
      }
      (recursiveParserWrapper.getMetadata.asScala.toVector, handlers.toVector)
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

    // Wrap results
    val mainContents = contentHandlers.headOption.toVector.flatMap(_.extractContents())
    val mainMetadata = rawMetadatas.headOption.flatMap(wrapMetadataTable)

    val subRawMetadatas = rawMetadatas.drop(1)
    val subContents = contentHandlers.drop(1)
    val subMetadatas = subRawMetadatas.map(wrapMetadataTable(_).toSeq) ++ subContents.map(_.extractContents())
    val mainMetadatas = mainContents ++ mainMetadata ++ extractArchiveTable(subRawMetadatas)

    if (subMetadatas.isEmpty) {
      Vector.empty
    } else {
      mainMetadatas ++
        extractTextPreview(mainMetadatas ++ subMetadatas.flatten) ++
        wrapSubMetadatas(subMetadatas)
    }
  }

  private[this] def createContentHandlers(): Handlers = {
    new Handlers(
      Some(new BodyContentHandler(autoParserSettings.textLimit)).filter(_ ⇒ autoParserSettings.textEnabled),
      Some(new ToXMLContentHandler()).filter(_ ⇒ autoParserSettings.xhtmlEnabled)
    )
  }

  private[this] final class Handlers(textHandler: Option[ContentHandler], xhtmlHandler: Option[ContentHandler]) {
    private[this] final class Handler(subHandlers: Seq[ContentHandler]) extends TeeContentHandler(subHandlers:_*) {
      override def toString: String = "" // RecursiveParserWrapper fix
    }

    def createHandler(): ContentHandler = {
      val subHandlers = Seq(textHandler, xhtmlHandler).flatten
      new Handler(subHandlers)
    }

    def extractContents(): Seq[Metadata] = {
      def extractText(handler: Option[ContentHandler], format: String): Option[Metadata] = {
        handler
          .map(_.toString)
          .filter(_.nonEmpty)
          .map(result ⇒ Metadata(Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.CONTENT)), Metadata.Value.Text(Metadata.Text(format, result))))
      }

      Seq(
        extractText(textHandler, "text/plain"),
        extractText(xhtmlHandler, "text/html")
      ).flatten
    }
  }
}
