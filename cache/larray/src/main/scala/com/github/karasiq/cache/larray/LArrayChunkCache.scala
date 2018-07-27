package com.github.karasiq.cache.larray

import scala.concurrent.{ExecutionContext, Future}

import com.karasiq.scalacache.larray.LArrayAsyncLRUCache
import com.karasiq.shadowcloud.cache.ChunkCache
import com.karasiq.shadowcloud.model.Chunk

private[cache] class LArrayChunkCache(size: Int)(implicit ec: ExecutionContext) extends ChunkCache {
  protected val cache = LArrayAsyncLRUCache[Chunk](size)

  def readCached(chunk: Chunk, getChunk: () ⇒ Future[Chunk]): Future[Chunk] = {
    val future = cache.getCached(chunk.withoutData, () ⇒ getChunk().map(_.data.encrypted))
    future.map(bytes ⇒ chunk.copy(data = chunk.data.copy(encrypted = bytes)))
  }
}
