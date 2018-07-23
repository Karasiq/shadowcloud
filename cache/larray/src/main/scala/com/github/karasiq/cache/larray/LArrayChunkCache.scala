package com.github.karasiq.cache.larray

import java.util.concurrent.Executors

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

import akka.util.ByteString
import xerial.larray.LByteArray

import com.karasiq.shadowcloud.cache.ChunkCache
import com.karasiq.shadowcloud.model.Chunk

// Tape LArray cache
class LArrayChunkCache(size: Long) extends ChunkCache {
  protected final case class CacheEntry(start: Long, size: Int, chunk: Chunk)
  protected implicit val executionContext = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())

  protected val cache = new LByteArray(size)
  protected val entries = mutable.TreeMap.empty[Long, CacheEntry]
  protected val entriesByChunk = mutable.AnyRefMap.empty[Chunk, CacheEntry]
  protected var currentPosition = 0

  protected def addCacheEntry(chunk: Chunk, data: ByteString): Unit = {
    if (data.isEmpty || data.length > size || entriesByChunk.contains(chunk)) return
    val position = if (data.length > size - currentPosition) 0 else currentPosition
    for (i ← data.indices) cache(position + i) = data(i)

    entries.range(position, position + data.length).foreach { case (position, entry) ⇒
      entries -= position
      entriesByChunk -= entry.chunk
    }

    val entry = CacheEntry(position, data.length, chunk)
    entries(position) = entry
    entriesByChunk(chunk) = entry
    currentPosition = position + data.length
    // println(s"Cache at $position = $chunk")
  }

  def readCached(chunk: Chunk, getChunk: () ⇒ Future[Chunk]): Future[Chunk] = {
    def fetchChunkAndSave() = {
      val future = getChunk()
      future.onComplete(_.foreach(chunk ⇒ addCacheEntry(chunk.withoutData, chunk.data.encrypted)))
      future
    }

    if (entriesByChunk.contains(chunk)) {
      val future = Future {
        entriesByChunk.get(chunk) match {
          case Some(entry) ⇒
            val data = new Array[Byte](entry.size)
            for (i ← data.indices) data(i) = cache(entry.start + i)
            Future.successful(chunk.copy(data = chunk.data.copy(encrypted = ByteString.fromArrayUnsafe(data))))
          case None ⇒
            fetchChunkAndSave()
        }
      }
      future.flatten
    } else {
      fetchChunkAndSave()
    }
  }

  override def finalize(): Unit = {
    executionContext.shutdown()
    cache.free
    super.finalize()
  }
}
