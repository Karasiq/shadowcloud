package com.karasiq.shadowcloud.metadata.tika

import akka.util.ByteString
import org.apache.tika.Tika

import com.karasiq.shadowcloud.metadata.MimeDetector

private[tika] object TikaMimeDetector {
  def apply(tika: Tika): TikaMimeDetector = {
    new TikaMimeDetector(tika)
  }
}

private[tika] final class TikaMimeDetector(tika: Tika) extends MimeDetector {
  def getMimeType(name: String, data: ByteString): Option[String] = {
    import com.karasiq.shadowcloud.utils.ByteStringUnsafe.implicits._
    Option(tika.detect(data.toArrayUnsafe, name))
  }
}
