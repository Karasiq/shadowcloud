package com.karasiq.shadowcloud.utils

import scala.collection.TraversableLike
import scala.concurrent.duration.FiniteDuration
import scala.language.{higherKinds, postfixOps}

import akka.util.ByteString
import com.typesafe.config.ConfigFactory

import com.karasiq.shadowcloud.index.Chunk

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
  def printHashes(hashes: Traversable[ByteString], limit: Int = 20): String = {
    if (hashes.isEmpty) return ""
    val size = hashes.size
    val sb = new StringBuilder(math.min(limit, size) * 22 + 10)
    hashes.filter(_.nonEmpty).take(limit).foreach { hash ⇒
      if (sb.nonEmpty) sb.append(", ")
      sb.append(HexString.encode(hash))
    }
    if (size > limit) sb.append(", (").append(size - limit).append(" more)")
    sb.result()
  }

  def printChunkHashes(chunks: Traversable[Chunk], limit: Int = 20): String = {
    printHashes(chunks.map(_.checksum.hash))
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

  val emptyConfig = ConfigFactory.empty()
}
