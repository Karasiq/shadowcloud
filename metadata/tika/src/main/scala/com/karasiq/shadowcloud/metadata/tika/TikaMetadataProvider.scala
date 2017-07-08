package com.karasiq.shadowcloud.metadata.tika

import com.typesafe.config.Config
import org.apache.tika.Tika
import org.apache.tika.config.TikaConfig

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.metadata.MetadataProvider

class TikaMetadataProvider(config: Config) extends MetadataProvider with ConfigImplicits {
  private[this] val tikaConfig = config.getConfig("metadata.tika")
  private[this] val tikaXmlConfig = config.optional(_.getString("xml-config-file"))
  private[this] val tika = tikaXmlConfig.fold(new Tika())(xml ⇒ new Tika(new TikaConfig(xml)))

  val detectors = Vector(
    TikaMimeDetector(tika)
  )

  val parsers = Vector(
    TikaAutoParser(tika, tikaConfig.getConfig("auto-parser"))
  )
}
