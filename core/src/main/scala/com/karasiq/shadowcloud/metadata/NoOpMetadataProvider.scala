package com.karasiq.shadowcloud.metadata

import com.karasiq.shadowcloud.metadata.internal.{NoOpMetadataParser, NoOpMimeDetector}

private[metadata] final class NoOpMetadataProvider extends MetadataProvider {
  val detectors: Seq[MimeDetector] = Vector(new NoOpMimeDetector)
  val parsers: Seq[MetadataParser] = Vector(new NoOpMetadataParser)
}
