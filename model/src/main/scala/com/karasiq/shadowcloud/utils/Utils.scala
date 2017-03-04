package com.karasiq.shadowcloud.utils

import com.karasiq.shadowcloud.index.Chunk

import scala.collection.TraversableLike
import scala.concurrent.duration.FiniteDuration
import scala.language.{higherKinds, postfixOps}

private[shadowcloud] object Utils {
  // -----------------------------------------------------------------------
  // Time
  // -----------------------------------------------------------------------
  @inline
  def timestamp: Long = {
    System.currentTimeMillis()
  }

  @inline
  def toScalaDuration(duration: java.time.Duration): FiniteDuration = {
    FiniteDuration(duration.toNanos, scala.concurrent.duration.NANOSECONDS)
  }

  // -----------------------------------------------------------------------
  // toString() utils
  // -----------------------------------------------------------------------
  def printHashes(chunks: Traversable[Chunk], limit: Int = 20): String = {
    val size = chunks.size
    val sb = new StringBuilder(math.min(limit, size) * 22 + 10)
    chunks.take(limit).foreach { chunk ⇒
      if (chunk.checksum.hash.nonEmpty) {
        if (sb.nonEmpty) sb.append(", ")
        sb.append(HexString.encode(chunk.checksum.hash))
      }
    }
    if (size > limit) sb.append(", (").append(size - limit).append(" more)")
    sb.result()
  }

  // -----------------------------------------------------------------------
  // Misc
  // -----------------------------------------------------------------------
  @inline
  def isSameChunk(chunk: Chunk, chunk1: Chunk): Boolean = {
    // chunk.withoutData == chunk1.withoutData
    chunk == chunk1
  }

  @inline
  def takeOrAll[T, Col[`T`] <: TraversableLike[T, Col[T]]](all: Col[T], count: Int): Col[T] = {
    if (count > 0) all.take(count) else all
  }
}
