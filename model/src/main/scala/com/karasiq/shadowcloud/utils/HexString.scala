package com.karasiq.shadowcloud.utils

import akka.util.ByteString

import scala.language.postfixOps

object HexString {
  private[this] val HEX_DIGITS = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  def encode(data: ByteString): String = {
    val length = data.length
    if (length == 0) return ""
    val out = new Array[Char](length << 1)
    var dataIndex, outIndex = 0
    while (dataIndex < length) {
      out(outIndex) = HEX_DIGITS((0xF0 & data(dataIndex)) >>> 4)
      outIndex += 1
      out(outIndex) = HEX_DIGITS(0x0F & data(dataIndex))
      outIndex += 1
      dataIndex += 1
    }
    new String(out)
  }

  def decode(data: String): ByteString = {
    @inline def hexCharToInt(char: Char): Int = Character.digit(char, 16)
    val length = data.length
    if (length == 0) return ByteString.empty
    require((length & 0x01) == 0)
    val outBuilder = ByteString.newBuilder
    outBuilder.sizeHint(length >> 1)
    var dataIndex = 0
    while (dataIndex < length) {
      var byte = hexCharToInt(data(dataIndex)) << 4
      dataIndex += 1
      byte = byte | hexCharToInt(data(dataIndex))
      dataIndex += 1
      outBuilder += (byte & 0xFF).toByte
    }
    outBuilder.result()
  }
}
