package com.karasiq.shadowcloud.utils

import akka.util.ByteString
import com.karasiq.shadowcloud.index.Chunk
import org.apache.commons.codec.binary.Hex

import scala.language.postfixOps

private[shadowcloud] object Utils {
  def toHexString(bs: ByteString): String = {
    Hex.encodeHexString(bs.toArray)
  }

  def parseHexString(hexString: String): ByteString = {
    ByteString(Hex.decodeHex(hexString.toCharArray))
  }

  def isSameChunk(chunk: Chunk, chunk1: Chunk): Boolean = {
    // chunk.withoutData == chunk1.withoutData
    chunk == chunk1
  }

  def timestamp: Long = {
    System.currentTimeMillis()
  }
}
