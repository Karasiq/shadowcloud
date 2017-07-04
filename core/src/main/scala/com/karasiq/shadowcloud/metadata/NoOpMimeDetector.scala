package com.karasiq.shadowcloud.metadata
import akka.util.ByteString

private[metadata] final class NoOpMimeDetector extends MimeDetector {
  def getMimeType(name: String, data: ByteString): Option[String] = None
}
