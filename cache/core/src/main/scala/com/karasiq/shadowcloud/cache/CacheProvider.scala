package com.karasiq.shadowcloud.cache

import com.karasiq.shadowcloud.config.CacheConfig

trait CacheProvider {
  def createChunkCache(config: CacheConfig): ChunkCache
}
