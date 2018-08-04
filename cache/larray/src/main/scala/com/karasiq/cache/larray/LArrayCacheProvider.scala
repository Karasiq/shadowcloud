package com.karasiq.cache.larray

import akka.actor.ActorSystem

import com.karasiq.shadowcloud.cache.{CacheProvider, ChunkCache}
import com.karasiq.shadowcloud.config.CacheConfig

private[cache] class LArrayCacheProvider(actorSystem: ActorSystem) extends CacheProvider {
  def createChunkCache(config: CacheConfig): ChunkCache = {
    new LArrayChunkCache(config.size)(actorSystem.dispatcher)
  }
}
