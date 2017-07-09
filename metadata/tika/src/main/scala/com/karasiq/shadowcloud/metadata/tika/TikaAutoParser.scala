package com.karasiq.shadowcloud.metadata.tika

import java.io.{InputStream, OutputStream}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

import com.typesafe.config.Config
import org.apache.tika.Tika
import org.apache.tika.metadata.{Metadata ⇒ TikaMetadata}
import org.apache.tika.parser.{ParseContext, RecursiveParserWrapper}
import org.apache.tika.sax._
import org.xml.sax.{ContentHandler, SAXException}

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
private[tika] final class TikaAutoParser(tika: Tika, val config: Config) extends TikaMetadataParser {
  // Configuration
  private[this] object autoParserConfig extends ConfigImplicits {
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
    super.canParse(name, mime) || (autoParserConfig.fb2Fix && name.endsWith(".fb2.zip"))
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
      Some(metadatas.map(Metadata.EmbeddedResources.EmbeddedResource(_)))
        .filter(_.nonEmpty)
        .map(resources ⇒ Metadata(Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.CONTENT)),
          Metadata.Value.EmbeddedResources(Metadata.EmbeddedResources(resources))))
    }

    def extractArchiveTable(subMetadatas: Seq[TikaMetadata]): Option[Metadata] = {
      val archiveFiles = subMetadatas.flatMap { md ⇒
        val path = Option(md.get("X-TIKA:embedded_resource_path")).map(_.split('/').filter(_.nonEmpty).toVector)
        val size = Option(md.get("Content-Length")).map(_.toLong)

        for {
          fullPath ← path if fullPath.nonEmpty
          size ← size
          path = fullPath.dropRight(1)
          name = fullPath.last
        } yield Metadata.ArchiveFiles.ArchiveFile(path, name, size)
      }

      Some(archiveFiles)
        .filter(_.nonEmpty)
        .map(files ⇒ Metadata(Some(Metadata.Tag("tika", "auto", Metadata.Tag.Disposition.PREVIEW)),
          Metadata.Value.ArchiveFiles(Metadata.ArchiveFiles(files))))
    }

    val (rawMetadatas, contentHandlers) = if (autoParserConfig.recursive) {
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
      } catch { case _: SAXException ⇒
        // Ignore
      }
      (recursiveParserWrapper.getMetadata.asScala.toVector, handlers.toVector)
    } else {
      // Plain parser
      val handler = createContentHandlers()
      try {
        this.parser.parse(inputStream, handler.createHandler(), metadata, new ParseContext)
      } catch { case _: SAXException ⇒
        // Ignore
      }
      (Vector(metadata), Vector(handler))
    }

    // Wrap results
    val results = contentHandlers.zip(rawMetadatas)
      .map { case (h, md) ⇒ h.extractContents() ++ wrapMetadataTable(md) }

    if (results.isEmpty) {
      Vector.empty
    } else {
      val (mainMetadata, subMetadatas) = results.splitAt(1)
      mainMetadata.flatten ++
        extractArchiveTable(rawMetadatas.drop(1)) ++
        wrapSubMetadatas(subMetadatas)
    }
  }

  private[this] def createContentHandlers(): Handlers = {
    new Handlers(
      Some(new BodyContentHandler(autoParserConfig.textLimit)).filter(_ ⇒ autoParserConfig.textEnabled),
      Some(new ToXMLContentHandler()).filter(_ ⇒ autoParserConfig.xhtmlEnabled)
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

      Seq(extractText(textHandler, "txt"), extractText(xhtmlHandler, "xhtml")).flatten
    }
  }
}
