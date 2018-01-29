package com.karasiq.shadowcloud.metadata.markdown

import com.typesafe.config.Config

import com.karasiq.shadowcloud.metadata.MetadataProvider

class MarkdownMetadataProvider(config: Config) extends MetadataProvider {
  protected val providerConfig = config.getConfig("metadata.markdown")
  
  val detectors = Nil
  val parsers = Vector(
    FlexmarkMetadataParser(providerConfig.getConfig("flexmark")),
    PlaintextMetadataParser(providerConfig.getConfig("plaintext"))
  )
}
