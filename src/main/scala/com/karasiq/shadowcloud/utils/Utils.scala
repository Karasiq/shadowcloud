package com.karasiq.shadowcloud.utils

import akka.util.ByteString
import com.karasiq.shadowcloud.index.Chunk
import org.apache.commons.codec.binary.Hex

import scala.collection.TraversableLike
import scala.concurrent.duration.FiniteDuration
import scala.language.{higherKinds, postfixOps}

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

  def toScalaDuration(duration: java.time.Duration): FiniteDuration = {
    FiniteDuration(duration.toNanos, scala.concurrent.duration.NANOSECONDS)
  }

  def printHashes(chunks: Traversable[Chunk], limit: Int = 20): String = {
    val size = chunks.size
    val sb = new StringBuilder(math.min(limit, size) * 22 + 10)
    chunks.take(limit).foreach { chunk ⇒
      if (chunk.checksum.hash.nonEmpty) {
        if (sb.nonEmpty) sb.append(", ")
        sb.append(toHexString(chunk.checksum.hash))
      }
    }
    if (size > limit) sb.append(", (").append(size - limit).append(" more)")
    sb.result()
  }

  def printSize(bytes: Long): String = {
    if (bytes < 1024) {
      s"$bytes bytes"
    } else {
      val units = Array("bytes", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
      val digitGroup = math.max(0, math.min(units.length - 1, (Math.log10(bytes) / Math.log10(1024)).toInt))
      f"${bytes / Math.pow(1024, digitGroup)}%.2f ${units(digitGroup)}"
    }
  }

  def takeOrAll[T, Col[`T`] <: TraversableLike[T, Col[T]]](all: Col[T], count: Int): Col[T] = {
    if (count > 0) all.take(count) else all
  }
}
