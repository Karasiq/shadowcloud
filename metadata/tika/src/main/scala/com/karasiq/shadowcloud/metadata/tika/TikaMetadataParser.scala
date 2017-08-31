package com.karasiq.shadowcloud.metadata.tika

import java.io.InputStream

import scala.collection.JavaConverters._
import scala.language.postfixOps

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.parser.{ParseContext, Parser}

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.metadata.utils.BlockingMetadataParser
import com.karasiq.shadowcloud.utils.Utils

/**
  * Abstract metadata parser
  */
private[tika] trait TikaMetadataParser extends BlockingMetadataParser {
  val parser: Parser
  val config: Config

  protected object stdParserSettings extends ConfigImplicits {
    val enabled = config.getBoolean("enabled")
    val extensions = config.getStringSet("extensions")
    val mimes = {
      val configMimes = config.getStringSet("mimes")
      if (config.getBoolean("handle-all-mimes")) {
        val internalList = parser.getSupportedTypes(new ParseContext).asScala.map(_.toString)
        internalList ++ configMimes
      } else {
        configMimes
      }
    }
  }

  protected def parseStream(metadata: TikaMetadata, inputStream: InputStream): Seq[Metadata]

  def canParse(name: String, mime: String): Boolean = {
    stdParserSettings.enabled && (stdParserSettings.mimes.contains(mime) || stdParserSettings.extensions.contains(Utils.getFileExtensionLowerCase(name)))
  }

  protected def parseMetadata(name: String, mime: String, inputStream: InputStream): Source[Metadata, NotUsed] = {
    val metadata = new TikaMetadata
    metadata.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, name)
    metadata.add(TikaCoreProperties.CONTENT_TYPE_HINT, mime)
    Source(parseStream(metadata, inputStream).toVector)
  }
}