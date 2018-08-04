package com.karasiq.shadowcloud.metadata

import com.karasiq.shadowcloud.providers.ModuleProvider

trait MetadataProvider extends ModuleProvider {
  def detectors: Seq[MimeDetector]
  def parsers: Seq[MetadataParser]
}
