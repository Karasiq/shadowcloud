package com.karasiq.shadowcloud.utils
import java.util.{Base64 ⇒ JBase64}

import akka.util.ByteString

object Base64 extends ByteStringEncoding {
  def encode(bytes: ByteString): String = {
    JBase64.getUrlEncoder.encodeToString(bytes.toArray)
  }

  def decode(string: String): ByteString = {
    ByteString(JBase64.getUrlDecoder.decode(string))
  }
}
