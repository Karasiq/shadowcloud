package com.karasiq.shadowcloud.metadata.tika

import com.karasiq.shadowcloud.metadata.MetadataProvider

class TikaMetadataProvider extends MetadataProvider {
  val detectors = Vector(
    TikaMimeDetector()
  )

  val parsers = Vector(
    TikaTextParser()
  )
}
