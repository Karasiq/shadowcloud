package com.karasiq.shadowcloud.metadata.javacv

import com.typesafe.config.Config

import com.karasiq.shadowcloud.metadata.MetadataProvider

private[javacv] class JavaCVMetadataProvider(rootConfig: Config) extends MetadataProvider {
  private[this] val config = rootConfig.getConfig("metadata.javacv")

  val detectors = Nil
  val parsers   = Vector(FFMPEGThumbnailCreator(config.getConfig("ffmpeg")), OpenCVThumbnailCreator(config.getConfig("opencv")))
}
