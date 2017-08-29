package com.karasiq.shadowcloud.utils

trait ByteStringEncoding {
  final type Bytes = akka.util.ByteString
  final type Encoded = String

  def encode(bytes: Bytes): Encoded
  def decode(string: Encoded): Bytes
}
