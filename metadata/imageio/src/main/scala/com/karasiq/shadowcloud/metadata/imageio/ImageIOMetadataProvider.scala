package com.karasiq.shadowcloud.metadata.imageio

import com.typesafe.config.Config

import com.karasiq.shadowcloud.metadata.{MetadataParser, MetadataProvider, MimeDetector}

class ImageIOMetadataProvider(rootConfig: Config) extends MetadataProvider {
  protected object imageioConfig {
    val config           = rootConfig.getConfig("metadata.imageio")
    val thumbnailsConfig = config.getConfig("thumbnails")
  }

  val detectors: Seq[MimeDetector] = Vector.empty

  val parsers: Seq[MetadataParser] = Vector(
    ImageIOThumbnailCreator(imageioConfig.thumbnailsConfig)
  )
}
