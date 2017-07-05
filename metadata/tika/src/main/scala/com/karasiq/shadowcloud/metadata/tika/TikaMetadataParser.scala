package com.karasiq.shadowcloud.metadata.tika

import java.io.{ByteArrayInputStream, InputStream}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

import akka.util.ByteString
import com.typesafe.config.Config
import org.apache.commons.io.FilenameUtils
import org.apache.tika.metadata.{TikaCoreProperties, Metadata ⇒ TikaMetadata}
import org.apache.tika.parser.{ParseContext, Parser}

import com.karasiq.shadowcloud.metadata.{Metadata, MetadataParser}

/**
  * Abstract metadata parser
  */
private[tika] trait TikaMetadataParser extends MetadataParser {
  val parser: Parser
  val config: Config

  protected lazy val extensions = config.getStringList("extensions").asScala.toSet
  protected lazy val mimes = {
    val configMimes = config.getStringList("mimes").asScala.toSet
    if (config.getBoolean("handle-all-mimes")) {
      parser.getSupportedTypes(new ParseContext).asScala.map(_.toString) ++ configMimes
    } else {
      configMimes
    }
  }

  protected def parseStream(metadata: TikaMetadata, inputStream: InputStream): Seq[Metadata]

  def canParse(name: String, mime: String): Boolean = {
    mimes.contains(mime) || extensions.contains(FilenameUtils.getExtension(name))
  }

  def parseMetadata(name: String, mime: String, data: ByteString): Seq[Metadata] = {
    val metadata = new TikaMetadata
    metadata.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, name)
    metadata.add(TikaCoreProperties.CONTENT_TYPE_HINT, mime)
    val stream = new ByteArrayInputStream(data.toArray)
    try {
      parseStream(metadata, stream)
    } catch { case NonFatal(_) ⇒
      Vector.empty // Ignore errors
    }
  }
}