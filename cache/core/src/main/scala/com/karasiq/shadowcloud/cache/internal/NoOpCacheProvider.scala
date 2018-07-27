package com.karasiq.shadowcloud.cache.internal

import scala.concurrent.Future

import com.karasiq.shadowcloud.cache.{CacheProvider, ChunkCache}
import com.karasiq.shadowcloud.config.CacheConfig
import com.karasiq.shadowcloud.model.Chunk

//noinspection ConvertExpressionToSAM
private[cache] final class NoOpCacheProvider extends CacheProvider {
  private[this] final class NoOpChunkCache extends ChunkCache {
    def readCached(chunk: Chunk, getChunk: () ⇒ Future[Chunk]): Future[Chunk] = getChunk()
  }

  def createChunkCache(config: CacheConfig): ChunkCache = new NoOpChunkCache
}
