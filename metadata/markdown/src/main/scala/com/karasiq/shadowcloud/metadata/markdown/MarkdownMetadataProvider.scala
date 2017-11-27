package com.karasiq.shadowcloud.metadata.markdown

import com.typesafe.config.Config

import com.karasiq.shadowcloud.metadata.MetadataProvider

class MarkdownMetadataProvider(config: Config) extends MetadataProvider {
  val detectors = Nil
  val parsers = Vector(FlexmarkMetadataParser(config.getConfig("metadata.markdown.flexmark")))
}
