package com.karasiq.shadowcloud.metadata.tika

import java.io.{InputStream, OutputStream}
import java.nio.charset.Charset

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.metadata.tika.utils.TikaConversions
import com.karasiq.shadowcloud.metadata.tika.utils.TikaUtils.ContentWrapper
import com.typesafe.config.Config
import org.apache.tika.Tika
import org.apache.tika.parser.{ParseContext, RecursiveParserWrapper}
import org.apache.tika.sax._
import org.xml.sax.ContentHandler
import org.xml.sax.helpers.DefaultHandler

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

private[tika] object TikaAutoParser {
  val ParserId = "auto"

  def apply(tika: Tika, config: Config): TikaAutoParser = {
    new TikaAutoParser(tika, config)
  }
}

/** Parses file with default Tika parser
  *
  * @param config Parser config
  */
//noinspection ScalaDeprecation
private[tika] final class TikaAutoParser(tika: Tika, val config: Config) extends TikaMetadataParser {
  // -----------------------------------------------------------------------
  // Settings
  // -----------------------------------------------------------------------
  private[this] object autoParserSettings extends ConfigImplicits {
    // Parser
    val recursive           = config.getBoolean("recursive")
    val fb2Fix              = config.getBoolean("fb2-fix")
    val fileListPreviewSize = config.getInt("file-list-preview-size")

    // Text handler
    val textEnabled     = config.getBoolean("text.enabled")
    val textLimit       = config.getBytesInt("text.limit")
    val textPreviewSize = config.getBytesInt("text.preview-size")
    val textMaxPreviews = config.getInt("text.max-previews")

    // XHTML handler
    val xhtmlEnabled = config.getBoolean("xhtml.enabled")
  }

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] val conversions = TikaConversions(TikaAutoParser.ParserId)
  override val parser           = tika.getParser

  // -----------------------------------------------------------------------
  // Parsing
  // -----------------------------------------------------------------------
  override def canParse(name: String, mime: String): Boolean = {
    super.canParse(name, mime) || (autoParserSettings.fb2Fix && name.endsWith(".fb2.zip"))
  }

  protected def parseStream(metadata: TikaMetadata, inputStream: InputStream): Seq[Metadata] = {
    val (rawMetadatas, contentWrappers) = parseMetadataTablesAndContents(metadata, inputStream, autoParserSettings.recursive)

    // Wrap results
    val mainContents = contentWrappers.headOption.toVector.flatMap(_.extractContents())
    val mainMetadata = rawMetadatas.headOption.flatMap(conversions.toMetadataTable)

    val subRawMetadatas = rawMetadatas.drop(1)
    val subContents     = contentWrappers.drop(1).map(_.extractContents())
    val subMetadatas    = subRawMetadatas.map(conversions.toMetadataTable(_).toSeq) ++ subContents

    mainMetadata.toVector ++
      mainMetadata.flatMap(conversions.toImageData) ++
      conversions.toTextPreviews(mainContents ++ subContents.flatten, autoParserSettings.textMaxPreviews, autoParserSettings.textPreviewSize) ++
      mainMetadata.flatMap(conversions.toDescription) ++
      mainContents ++
      conversions.toArchiveTables(subMetadatas.flatten, autoParserSettings.fileListPreviewSize) ++
      conversions.toEmbeddedResources(subMetadatas)
  }

  private[this] def parseMetadataTablesAndContents(
      metadata: TikaMetadata,
      inputStream: InputStream,
      recursive: Boolean
  ): (Vector[TikaMetadata], Vector[ContentWrapper]) = {
    if (recursive) {
      // Recursive parser
      val handlers = new ArrayBuffer[ContentWrapper]()
      val contentHandlerFactory = new ContentHandlerFactory {
        def getNewContentHandler: ContentHandler = {
          val contentWrapper = createContentWrapper()
          handlers += contentWrapper
          contentWrapper.toContentHandler
        }
        def getNewContentHandler(os: OutputStream, encoding: String): ContentHandler = this.getNewContentHandler
        def getNewContentHandler(os: OutputStream, charset: Charset): ContentHandler = this.getNewContentHandler
      }

      val parser = new RecursiveParserWrapper(this.parser, contentHandlerFactory)
      try {
        parser.parse(inputStream, null, metadata, new ParseContext)
      } catch {
        case NonFatal(_) ⇒
        // Ignore
      }

      (parser.getMetadata.asScala.toVector, handlers.toVector)
    } else {
      // Plain parser
      val contentWrapper = createContentWrapper()

      try {
        this.parser.parse(inputStream, contentWrapper.toContentHandler, metadata, new ParseContext)
      } catch {
        case NonFatal(_) ⇒
        // Ignore
      }

      (Vector(metadata), Vector(contentWrapper))
    }
  }

  @inline
  private[this] def createContentWrapper() = {
    ContentWrapper(
      TikaAutoParser.ParserId,
      if (autoParserSettings.textEnabled) autoParserSettings.textLimit else 0,
      autoParserSettings.xhtmlEnabled
    )
  }
}
