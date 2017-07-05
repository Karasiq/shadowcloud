package com.karasiq.shadowcloud.metadata.tika

import com.typesafe.config.Config

import com.karasiq.shadowcloud.metadata.MetadataProvider

class TikaMetadataProvider(config: Config) extends MetadataProvider {
  private[this] val tikaConfig = config.getConfig("metadata.tika")

  val detectors = Vector(
    TikaMimeDetector()
  )

  val parsers = Vector(
    TikaTextParser(tikaConfig.getConfig("text-parser"))
  )
}
