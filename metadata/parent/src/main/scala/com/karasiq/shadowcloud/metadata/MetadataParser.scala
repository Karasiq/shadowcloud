package com.karasiq.shadowcloud.metadata

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString

trait MetadataParser {
  def canParse(name: String, mime: String): Boolean
  def parseMetadata(name: String, mime: String): Flow[ByteString, Metadata, NotUsed]
}
