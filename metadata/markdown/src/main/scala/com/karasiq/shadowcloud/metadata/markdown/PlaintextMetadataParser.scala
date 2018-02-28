package com.karasiq.shadowcloud.metadata.markdown

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.metadata.{Metadata, MetadataParser}
import com.karasiq.shadowcloud.metadata.config.MetadataParserConfig
import com.karasiq.shadowcloud.metadata.Metadata.Tag
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.karasiq.shadowcloud.utils.Utils

object PlaintextMetadataParser {
  def apply(config: Config): PlaintextMetadataParser = {
    new PlaintextMetadataParser(config)
  }
}

class PlaintextMetadataParser(config: Config) extends MetadataParser {
  protected object settings {
    val parserConfig = MetadataParserConfig(config)
    val sizeLimit = config.getBytesInt("size-limit")
  }

  def canParse(name: String, mime: String): Boolean = {
    settings.parserConfig.canParse(name, mime)
  }

  def parseMetadata(name: String, mime: String): Flow[ByteString, Metadata, NotUsed] = {
    Flow[ByteString]
      .via(ByteStreams.truncate(settings.sizeLimit))
      .via(ByteStreams.concat)
      .filter(_.nonEmpty)
      .map { bytes ⇒
        val preview = Utils.takeWords(bytes.utf8String, settings.sizeLimit)
        Metadata(Some(Tag("markdown", "plaintext", Tag.Disposition.PREVIEW)), Metadata.Value.Text(Metadata.Text("text/plain", preview)))
      }
      .named("plaintextParse")
  }
}
