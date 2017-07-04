package com.karasiq.shadowcloud.metadata

trait MetadataProvider {
  def detectors: Seq[MimeDetector]
  def parsers: Seq[MetadataParser]
}
