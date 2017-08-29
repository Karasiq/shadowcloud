package com.karasiq.shadowcloud.utils

import scala.language.postfixOps

import akka.util.ByteString

object HexString extends ByteStringEncoding {
  private[this] val HEX_DIGITS = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  def encode(data: Bytes): Encoded = {
    if (data.isEmpty) return ""
    
    val halfLength = data.length << 1
    val outArray = new Array[Char](halfLength)
    
    var dataIndex, outIndex = 0
    while (dataIndex < data.length) {
      outArray(outIndex) = HEX_DIGITS((0xF0 & data(dataIndex)) >>> 4)
      outIndex += 1
      outArray(outIndex) = HEX_DIGITS(0x0F & data(dataIndex))
      outIndex += 1
      dataIndex += 1
    }
    
    new String(outArray)
  }

  def decode(data: Encoded): Bytes = {
    @inline def hexCharToInt(char: Char): Int = Character.digit(char, 16)
    
    if (data.isEmpty) return ByteString.empty
    require((data.length & 0x01) == 0, "Not a hex string: " + data)
    
    val outBuilder = ByteString.newBuilder
    val doubleLength = data.length >> 1
    outBuilder.sizeHint(doubleLength)
    
    var dataIndex = 0
    while (dataIndex < data.length) {
      var byte = hexCharToInt(data(dataIndex)) << 4
      dataIndex += 1
      byte = byte | hexCharToInt(data(dataIndex))
      dataIndex += 1
      outBuilder += (byte & 0xFF).toByte
    }
    
    outBuilder.result()
  }
}
