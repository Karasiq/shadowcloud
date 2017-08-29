package com.karasiq.shadowcloud.utils.encoding

import java.util.{Base64 ⇒ JBase64}

import akka.util.ByteString

object Base64 extends ByteStringEncoding {
  def encode(bytes: BytesT): EncodedT = {
    JBase64.getUrlEncoder.encodeToString(bytes.toArray)
  }

  def decode(string: EncodedT): BytesT = {
    ByteString(JBase64.getUrlDecoder.decode(string))
  }
}
