package com.karasiq.shadowcloud.utils

import java.util.{Base64 ⇒ JBase64}

import akka.util.ByteString

object Base64 extends ByteStringEncoding {
  def encode(bytes: Bytes): Encoded = {
    JBase64.getUrlEncoder.encodeToString(bytes.toArray)
  }

  def decode(string: Encoded): Bytes = {
    ByteString(JBase64.getUrlDecoder.decode(string))
  }
}
