package com.karasiq.shadowcloud.metadata.internal

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString

import com.karasiq.shadowcloud.metadata.{Metadata, MetadataParser}

private[metadata] final class NoOpMetadataParser extends MetadataParser {
  def canParse(name: String, mime: String): Boolean = false

  def parseMetadata(name: String, mime: String): Flow[ByteString, Metadata, NotUsed] = {
    Flow.fromSinkAndSource(Sink.cancelled, Source.empty)
  }
}
