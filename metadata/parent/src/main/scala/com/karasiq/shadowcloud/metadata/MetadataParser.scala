package com.karasiq.shadowcloud.metadata

import akka.util.ByteString

trait MetadataParser {
  def canParse(name: String, mime: String): Boolean
  def parseMetadata(name: String, mime: String, data: ByteString): Seq[Metadata]
}
