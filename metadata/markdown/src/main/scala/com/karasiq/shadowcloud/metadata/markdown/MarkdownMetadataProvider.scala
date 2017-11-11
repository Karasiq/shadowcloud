package com.karasiq.shadowcloud.metadata.markdown

import com.karasiq.shadowcloud.metadata.MetadataProvider

class MarkdownMetadataProvider extends MetadataProvider {
  val detectors = Nil
  val parsers = Vector(FlexmarkMetadataParser())
}
