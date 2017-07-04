package com.karasiq.shadowcloud.metadata
import akka.util.ByteString

private[metadata] final class NoOpMetadataParser extends MetadataParser {
  def canParse(name: String, mime: String): Boolean = false
  def parseMetadata(name: String, mime: String, data: ByteString): Seq[Metadata] = Vector.empty
}
