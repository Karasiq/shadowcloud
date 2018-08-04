package com.karasiq.cache.larray

import scala.concurrent.{ExecutionContext, Future}

import com.karasiq.scalacache.larray.LArrayAsyncLRUCache
import com.karasiq.shadowcloud.cache.ChunkCache
import com.karasiq.shadowcloud.model.Chunk

private[cache] class LArrayChunkCache(size: Int)(implicit ec: ExecutionContext) extends ChunkCache {
  protected val cache = LArrayAsyncLRUCache[Chunk](size)
  protected var lastChunk: Chunk = _

  def readCached(chunk: Chunk, getChunk: () ⇒ Future[Chunk]): Future[Chunk] = {
    // Single read optimization
    val last = this.lastChunk
    if (last == chunk) return Future.successful(last)

    val future = cache.getCached(chunk.withoutData, () ⇒ getChunk().map(_.data.plain))
    future.map { bytes ⇒
      val chunkWithData = chunk.copy(data = chunk.data.copy(plain = bytes))
      this.lastChunk = chunkWithData
      chunkWithData
    }
  }
}
