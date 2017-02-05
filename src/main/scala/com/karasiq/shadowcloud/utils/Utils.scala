package com.karasiq.shadowcloud.utils

import akka.util.ByteString
import org.apache.commons.codec.binary.Hex

import scala.language.postfixOps

private[shadowcloud] object Utils {
  def toHexString(bs: ByteString): String = {
    Hex.encodeHexString(bs.toArray)
  }

  def parseHexString(hexString: String): ByteString = {
    ByteString(Hex.decodeHex(hexString.toCharArray))
  }
}
