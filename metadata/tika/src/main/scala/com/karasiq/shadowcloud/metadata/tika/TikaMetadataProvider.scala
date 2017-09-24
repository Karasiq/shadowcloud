package com.karasiq.shadowcloud.metadata.tika

import com.typesafe.config.Config
import org.apache.tika.Tika
import org.apache.tika.config.TikaConfig

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.metadata.MetadataProvider

class TikaMetadataProvider(rootConfig: Config) extends MetadataProvider {
  protected object tikaConfig extends ConfigImplicits {
    val config = rootConfig.getConfig("metadata.tika")
    val xmlConfigFile = config.optional(_.getString("xml-config-file"))
    val autoParserConfig = config.getConfig("auto-parser")
  }

  protected val tika = tikaConfig.xmlConfigFile.fold(new Tika())(xml ⇒ new Tika(new TikaConfig(xml)))

  val detectors = Vector(
    TikaMimeDetector(tika)
  )

  val parsers = Vector(
    TikaAutoParser(tika, tikaConfig.autoParserConfig)
  )
}
