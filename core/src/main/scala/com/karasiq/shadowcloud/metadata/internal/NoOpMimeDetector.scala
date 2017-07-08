package com.karasiq.shadowcloud.metadata.internal

import akka.util.ByteString

import com.karasiq.shadowcloud.metadata.MimeDetector

private[metadata] final class NoOpMimeDetector extends MimeDetector {
  def getMimeType(name: String, data: ByteString): Option[String] = None
}
