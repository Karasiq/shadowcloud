package com.karasiq.shadowcloud.utils

import akka.Done
import akka.stream.IOResult
import akka.util.ByteString
import com.karasiq.shadowcloud.index.Chunk
import org.apache.commons.codec.binary.Hex

import scala.collection.TraversableLike
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{higherKinds, postfixOps}
import scala.util.Try

private[shadowcloud] object Utils {
  // -----------------------------------------------------------------------
  // Hex strings
  // -----------------------------------------------------------------------
  def toHexString(bs: ByteString): String = {
    Hex.encodeHexString(bs.toArray)
  }

  def parseHexString(hexString: String): ByteString = {
    ByteString(Hex.decodeHex(hexString.toCharArray))
  }

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

  // -----------------------------------------------------------------------
  // Futures
  // -----------------------------------------------------------------------
  def onIoComplete(future: Future[IOResult])(pf: PartialFunction[Try[Long], Unit])(implicit ec: ExecutionContext): Unit = {
    import scala.util.{Failure, Success}
    @inline def onSuccess(bytes: Long): Unit = {
      val value = Success(bytes)
      if (pf.isDefinedAt(value)) pf(value)
    }
    @inline def onFailure(error: Throwable): Unit = {
      val value = Failure(error)
      if (pf.isDefinedAt(value)) pf(value)
    }
    future.onComplete {
      case Success(IOResult(written, Success(Done))) ⇒ onSuccess(written)
      case Success(IOResult(_, Failure(error))) ⇒ onFailure(error)
      case Failure(error) ⇒ onFailure(error)
    }
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
