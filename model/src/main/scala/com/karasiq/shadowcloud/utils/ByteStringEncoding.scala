package com.karasiq.shadowcloud.utils

import akka.util.ByteString

trait ByteStringEncoding {
  def encode(bytes: ByteString): String
  def decode(string: String): ByteString
}
