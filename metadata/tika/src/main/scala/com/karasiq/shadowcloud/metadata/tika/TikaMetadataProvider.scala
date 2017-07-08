package com.karasiq.shadowcloud.metadata.tika

import com.typesafe.config.Config
import org.apache.tika.Tika

import com.karasiq.shadowcloud.metadata.MetadataProvider

class TikaMetadataProvider(config: Config) extends MetadataProvider {
  private[this] val tika = new Tika()
  private[this] val tikaConfig = config.getConfig("metadata.tika")

  val detectors = Vector(
    TikaMimeDetector(tika)
  )

  val parsers = Vector(
    TikaAutoParser(tika, tikaConfig.getConfig("auto-parser"))
  )
}
