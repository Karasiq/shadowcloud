package com.karasiq.shadowcloud.metadata.tika

import java.io.InputStream

import scala.collection.JavaConverters._
import scala.language.postfixOps

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.parser.{ParseContext, Parser}

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.metadata.config.MetadataParserConfig
import com.karasiq.shadowcloud.metadata.utils.BlockingMetadataParser

/**
  * Abstract metadata parser
  */
private[tika] trait TikaMetadataParser extends BlockingMetadataParser {
  val parser: Parser
  val config: Config

  protected object stdParserSettings extends ConfigImplicits {
    val parserConfig = {
      val pc = MetadataParserConfig(config)
      if (config.withDefault(false, _.getBoolean("handle-all-mimes"))) {
        val internalList = parser.getSupportedTypes(new ParseContext).asScala.map(_.toString)
        pc.copy(mimes = internalList.toSet ++ pc.mimes)
      } else {
        pc
      }
    }
  }

  protected def parseStream(metadata: TikaMetadata, inputStream: InputStream): Seq[Metadata]

  def canParse(name: String, mime: String): Boolean = {
    stdParserSettings.parserConfig.canParse(name, mime)
  }

  protected def parseMetadata(name: String, mime: String, inputStream: InputStream): Source[Metadata, NotUsed] = {
    val metadata = new TikaMetadata
    metadata.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, name)
    metadata.add(TikaCoreProperties.CONTENT_TYPE_HINT, mime)
    Source(parseStream(metadata, inputStream).toVector)
  }
}